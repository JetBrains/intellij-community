/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.folding;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyFoldingBuilder extends CustomFoldingBuilder implements DumbAware {

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    appendDescriptors(root, descriptors, new HashSet<>());
  }

  private static void appendDescriptors(final PsiElement element, List<? super FoldingDescriptor> descriptors, Set<? super PsiElement> usedComments) {
    ASTNode node = element.getNode();
    if (node == null) return;
    IElementType type = node.getElementType();

    if (TokenSets.BLOCK_SET.contains(type) && !isSingleHighLevelClassBody(element) || type == GroovyElementTypes.CLOSABLE_BLOCK) {
      if (isMultiline(element)) {
        collapseBlock(descriptors, element);
      }
    }
    // comments
    if (type.equals(GroovyTokenTypes.mML_COMMENT) &&
        isMultiline(element) &&
        isWellEndedComment(element)) {
      descriptors.add(new NamedFoldingDescriptor(node, node.getTextRange(), null, "/*...*/",
                                                 isFileHeader(node) && JavaCodeFoldingSettings.getInstance().isCollapseFileHeader(), Collections.emptySet()));
    }
    if (type.equals(GroovyDocElementTypes.GROOVY_DOC_COMMENT) &&
        isMultiline(element) &&
        isWellEndedComment(element)) {
      descriptors.add(new NamedFoldingDescriptor(node, node.getTextRange(), null, "/**...*/", isFileHeader(node) ? JavaCodeFoldingSettings.getInstance().isCollapseFileHeader() : JavaCodeFoldingSettings.getInstance().isCollapseJavadocs(), Collections.emptySet()));
    }

    if (type.equals(GroovyTokenTypes.mSL_COMMENT) && usedComments.add(element) && !isCustomRegionElement(element)) {
      PsiElement end = null;
      for (PsiElement current = element.getNextSibling(); current != null; current = current.getNextSibling()) {
        if (PsiImplUtil.isWhiteSpaceOrNls(current)) continue;

        IElementType elementType = current.getNode().getElementType();
        if (elementType == GroovyTokenTypes.mSL_COMMENT && usedComments.add(current) && !isCustomRegionElement(current)) {
          end = current;
          continue;
        }
        break;
      }
      if (end != null) {
        final TextRange range = new TextRange(element.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
        descriptors.add(new NamedFoldingDescriptor(node, range, null, "...", isFileHeader(node) ? JavaCodeFoldingSettings.getInstance().isCollapseFileHeader() : JavaCodeFoldingSettings.getInstance().isCollapseEndOfLineComments(), Collections.emptySet()));
      }
    }

    //multiline strings
    addFoldingForStrings(descriptors, node);

    Set<PsiElement> newUsedComments = new HashSet<>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      appendDescriptors(child, descriptors, newUsedComments);
    }

    if (element instanceof GroovyFile) {
      processImports(descriptors, ((GroovyFile)element).getImportStatements());
    }
  }

  private static boolean isCollapseBlock(@NotNull ASTNode node) {
    IElementType type = node.getElementType();
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    if ((type == GroovyElementTypes.OPEN_BLOCK || type == GroovyElementTypes.CONSTRUCTOR_BODY) && node.getTreeParent().getElementType() ==
                                                                                                                                    GroovyElementTypes.METHOD_DEFINITION) {
      return settings.isCollapseMethods();
    }

    if (type == GroovyElementTypes.CLOSABLE_BLOCK) {
      return settings.isCollapseAnonymousClasses();
    }

    if (type == GroovyElementTypes.CLASS_BODY) {
      final PsiElement parent = node.getPsi().getParent();
      if (parent instanceof PsiClass) {
        if (parent instanceof PsiAnonymousClass) {
          return settings.isCollapseAnonymousClasses();
        }
        if (((PsiClass)parent).getContainingClass() != null) {
          return settings.isCollapseInnerClasses();
        }
      }
    }

    return false;
  }

  private static void collapseBlock(List<? super FoldingDescriptor> descriptors, @NotNull PsiElement psi) {
    ASTNode node = psi.getNode();
    boolean collapse = isCollapseBlock(node);
    if (psi instanceof GrCodeBlock) {
      final int lineFeedCount = StringUtil.countChars(psi.getText(), '\n');
      if (lineFeedCount <= 2) {
        final PsiElement lbrace = ((GrCodeBlock)psi).getLBrace();
        final PsiElement rbrace = ((GrCodeBlock)psi).getRBrace();
        if (lbrace != null && rbrace != null) {
          final PsiElement next = lbrace.getNextSibling();
          final PsiElement prev = rbrace.getPrevSibling();
          if (PsiImplUtil.isWhiteSpaceOrNls(next) && PsiImplUtil.isWhiteSpaceOrNls(prev)) {
            final FoldingGroup group = FoldingGroup.newGroup("block_group");
            descriptors.add(new NamedFoldingDescriptor(node, new TextRange(lbrace.getTextRange().getStartOffset(), next.getTextRange().getEndOffset()), group, "{", collapse, Collections.emptySet()));
            descriptors.add(new NamedFoldingDescriptor(node, new TextRange(prev.getTextRange().getStartOffset(), rbrace.getTextRange().getEndOffset()), group, "}", collapse, Collections.emptySet()));
            return;
          }
        }
      }
    }
    descriptors.add(new NamedFoldingDescriptor(node, node.getTextRange(), null, "{...}", collapse, Collections.emptySet()));
  }

  private static boolean isSingleHighLevelClassBody(PsiElement element) {
    if (!(element instanceof GrTypeDefinitionBody)) return false;

    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrTypeDefinition)) return false;

    final GrTypeDefinition clazz = (GrTypeDefinition)parent;
    if (clazz.isAnonymous() || clazz.getContainingClass() != null) return false;

    final PsiFile file = element.getContainingFile();
    return file instanceof GroovyFile && ((GroovyFile)file).getClasses().length == 1;
  }

  private static void addFoldingForStrings(List<? super FoldingDescriptor> descriptors, ASTNode node) {
    if (!isMultiLineStringLiteral(node)) return;

    if (!node.getElementType().equals(GroovyElementTypes.GSTRING) && !node.getElementType().equals(GroovyElementTypes.REGEX)) {
      descriptors.add(new NamedFoldingDescriptor(node, node.getTextRange(), null, multiLineStringLiteralPlaceholder(node), false, Collections.emptySet()));
      return;
    }

    final GrString grString = (GrString)node.getPsi();
    if (grString == null) return;

    final GrStringInjection[] injections = grString.getInjections();
    if (injections.length == 0) {
      descriptors.add(new NamedFoldingDescriptor(node, node.getTextRange(), null, multiLineStringLiteralPlaceholder(node), false, Collections.emptySet()));
      return;
    }
    final FoldingGroup group = FoldingGroup.newGroup("GString");
    final TextRange nodeRange = node.getTextRange();
    int startOffset = nodeRange.getStartOffset();

    GrStringInjection injection = injections[0];
    TextRange injectionRange = injection.getTextRange();
    if (startOffset + 1 < injectionRange.getStartOffset()) {
      final String start_quote = GrStringUtil.getStartQuote(node.getText());
      descriptors.add(new NamedFoldingDescriptor(node, new TextRange(startOffset, injectionRange.getStartOffset()), group, start_quote, false, Collections.emptySet()));
    }

    startOffset = injectionRange.getEndOffset();
    for (int i = 1; i < injections.length; i++) {
      injection = injections[i];
      injectionRange = injection.getTextRange();
      final int endOffset = injectionRange.getStartOffset();
      if (endOffset - startOffset >= 2) {
        descriptors.add(new NamedFoldingDescriptor(injection.getNode().getTreePrev(), new TextRange(startOffset, endOffset), group, " ", false, Collections.emptySet()));
      }
      startOffset = injectionRange.getEndOffset();
    }
    if (startOffset + 1 < nodeRange.getEndOffset()) {
      final String end_quote = GrStringUtil.getEndQuote(node.getText());
      descriptors.add(new NamedFoldingDescriptor(node.getLastChildNode(), new TextRange(startOffset, nodeRange.getEndOffset()), group, end_quote, false, Collections.emptySet()));
    }
  }

  private static void processImports(final List<? super FoldingDescriptor> descriptors, GrImportStatement[] imports) {
    if (imports.length < 2) return;

    PsiElement first = imports[0];
    while (first != null) {
      PsiElement marker = first;
      PsiElement next = first.getNextSibling();
      while (next instanceof GrImportStatement || next instanceof LeafPsiElement) {
        if (next instanceof GrImportStatement) marker = next;
        next = next.getNextSibling();
      }
      if (marker != first) {
        int start = first.getTextRange().getStartOffset();
        int end = marker.getTextRange().getEndOffset();
        int tail = "import ".length();
        if (start + tail < end && !JavaFoldingBuilderBase.hasErrorElementsNearby(first.getContainingFile(), start, end)) {
          FoldingDescriptor descriptor = new NamedFoldingDescriptor(first.getNode(), new TextRange(start + tail, end), null, "...", JavaCodeFoldingSettings.getInstance().isCollapseImports(), Collections
            .emptySet());
          // imports are often added/removed automatically, so we enable autoupdate of folded region for foldings even if it's collapsed
          descriptor.setCanBeRemovedWhenCollapsed(true);
          descriptors.add(descriptor);
        }
      }
      while (!(next instanceof GrImportStatement) && next != null) next = next.getNextSibling();
      first = next;
    }
  }

  private static boolean isWellEndedComment(PsiElement element) {
    return  element.getText().endsWith("*/");
  }

  private static boolean isMultiline(PsiElement element) {
    String text = element.getText();
    return text.contains("\n") || text.contains("\r") || text.contains("\r\n");
  }

  @Nullable
  @Override
  protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    return null;
  }

  @NotNull
  private static String multiLineStringLiteralPlaceholder(@NotNull ASTNode node) {
    final String start_quote = GrStringUtil.getStartQuote(node.getText());
    final String end_quote = GrStringUtil.getEndQuote(node.getText());
    return start_quote + "..." + end_quote;
  }

  private static boolean isFileHeader(ASTNode node) {
    PsiElement element = node.getPsi();
    PsiElement parent = element.getParent();
    if (parent instanceof GroovyFile) {
      PsiElement firstChild = parent.getFirstChild();
      if (firstChild instanceof PsiWhiteSpace) {
        firstChild = firstChild.getNextSibling();
      }
      return element.equals(firstChild);
    }

    return false;
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private static boolean isMultiLineStringLiteral(ASTNode node) {
    return (TokenSets.STRING_LITERAL_SET.contains(node.getElementType()) ||
            node.getElementType().equals(GroovyElementTypes.GSTRING) ||
            node.getElementType().equals(GroovyElementTypes.REGEX)) &&
           isMultiline(node.getPsi()) &&
           GrStringUtil.isWellEndedString(node.getPsi());
  }

  @Override
  protected boolean isCustomFoldingCandidate(@NotNull ASTNode node) {
    return node.getElementType() == GroovyTokenTypes.mSL_COMMENT;
  }

  @Override
  protected boolean isCustomFoldingRoot(@NotNull ASTNode node) {
    IElementType nodeType = node.getElementType();
    return nodeType == GroovyElementTypes.CLASS_DEFINITION || nodeType == GroovyElementTypes.OPEN_BLOCK;
  }
}
