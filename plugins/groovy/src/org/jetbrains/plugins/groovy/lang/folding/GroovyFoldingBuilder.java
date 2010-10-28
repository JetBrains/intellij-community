/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyFoldingBuilder implements FoldingBuilder, GroovyElementTypes, DumbAware {

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node.getPsi(), descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void appendDescriptors(PsiElement element, List<FoldingDescriptor> descriptors) {
    ASTNode node = element.getNode();
    if (node == null) return;
    IElementType type = node.getElementType();

    if (BLOCK_SET.contains(type) || type == CLOSABLE_BLOCK) {
      if (isMultiline(element)) {
        descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      }
    }
    // comments
    if ((type.equals(mML_COMMENT) || type.equals(GROOVY_DOC_COMMENT)) &&
        isMultiline(element) &&
        isWellEndedComment(element)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    //multiline strings
    addFoldingForStrings(descriptors, node);

    PsiElement child = element.getFirstChild();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getNextSibling();
    }

    if (element instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)element;
      addFoldingsForImports(descriptors, file);
    }
  }

  private static void addFoldingForStrings(List<FoldingDescriptor> descriptors, ASTNode node) {
    if (!isMultiLineStringLiteral(node)) return;

    if (!node.getElementType().equals(GSTRING)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      return;
    }

    final GrString grString = (GrString)node.getPsi();
    if (grString == null) return;

    final GrStringInjection[] injections = grString.getInjections();
    if (injections.length == 0) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      return;
    }
    final String quote = GrStringUtil.getStartQuote(node.getText());
    final FoldingGroup group = FoldingGroup.newGroup("GString");
    final TextRange nodeRange = node.getTextRange();
    int start = nodeRange.getStartOffset();


    GrStringInjection injection = injections[0];
    boolean hasClosableBlock = injection.getClosableBlock() != null;
    final String holderText = quote + "..." + (hasClosableBlock ? "${" : "$");
    TextRange injectionRange = injection.getTextRange();
    descriptors.add(new FoldingDescriptor(node, new TextRange(start, injectionRange.getStartOffset() + (hasClosableBlock ? 2 : 1)), group) {
      @Override
      public String getPlaceholderText() {
        return holderText;
      }
    });
    start = injectionRange.getEndOffset() - (hasClosableBlock ? 1 : 0);
    for (int i = 1; i < injections.length; i++) {
      injection = injections[i];
      boolean hasClosableBlockNew = injection.getClosableBlock() != null;
      injectionRange = injection.getTextRange();
      final String text = (hasClosableBlock ? "}" : "") + "..." + (hasClosableBlockNew ? "${" : "$");
      descriptors.add(new FoldingDescriptor(injection.getNode().getTreePrev(),
                                            new TextRange(start, injectionRange.getStartOffset() + (hasClosableBlockNew ? 2 : 1)), group) {
        @Override
        public String getPlaceholderText() {
          return text;
        }
      });

      hasClosableBlock = hasClosableBlockNew;
      start = injectionRange.getEndOffset() - (hasClosableBlock ? 1 : 0);
    }
    final String text = (hasClosableBlock ? "}" : "") + "..." + quote;
    descriptors.add(new FoldingDescriptor(node.getLastChildNode(), new TextRange(start, nodeRange.getEndOffset()), group) {
      @Override
      public String getPlaceholderText() {
        return text;
      }
    });
  }

  private static void addFoldingsForImports(final List<FoldingDescriptor> descriptors, final GroovyFile file) {
    final GrImportStatement[] statements = file.getImportStatements();
    if (statements.length > 1) {
      PsiElement first = statements[0];
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
          if (start + tail < end) {
            descriptors.add(new FoldingDescriptor(first.getNode(), new TextRange(start + tail, end)));
          }
        }
        while (!(next instanceof GrImportStatement) && next != null) next = next.getNextSibling();
        first = next;
      }
    }
  }

  private static boolean isWellEndedComment(PsiElement element) {
    if (element instanceof PsiComment) {
      PsiComment comment = (PsiComment)element;
      ASTNode node = comment.getNode();
      if (node != null &&
          node.getElementType() == mML_COMMENT &&
          node.getText().endsWith("*/")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isWellEndedString(PsiElement element) {
    final String text = element.getText();
    return text.endsWith("'''") || text.endsWith("\"\"\"");
  }

  private static boolean isMultiline(PsiElement element) {
    String text = element.getText();
    return text.contains("\n") || text.contains("\r") || text.contains("\r\n");
  }

  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType elemType = node.getElementType();
    if (BLOCK_SET.contains(elemType) || elemType == CLOSABLE_BLOCK) {
      return "{...}";
    }
    if (elemType.equals(mML_COMMENT)) {
      return "/*...*/";
    }
    if (elemType.equals(GROOVY_DOC_COMMENT)) {
      return "/**...*/";
    }
    if (IMPORT_STATEMENT.equals(elemType)) {
      return "...";
    }
    if (isMultiLineStringLiteral(node)) {
      final String quote = GrStringUtil.getStartQuote(node.getText());
      return quote +"..."+ quote;
    }
    return null;
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return node.getElementType() == IMPORT_STATEMENT && JavaCodeFoldingSettings.getInstance().isCollapseImports();
  }

  private static boolean isMultiLineStringLiteral(ASTNode node) {
    return (STRING_LITERAL_SET.contains(node.getElementType()) || node.getElementType().equals(GSTRING)) &&
     isMultiline(node.getPsi()) &&
     isWellEndedString(node.getPsi());
  }
}
