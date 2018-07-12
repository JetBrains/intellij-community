// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;


/**
 * @author ilyas
 */
public class GroovyFormattingModelBuilder implements FormattingModelBuilder {
  @Override
  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    ASTNode node = element.getNode();
    assert node != null;
    PsiFile containingFile = element.getContainingFile().getViewProvider().getPsi(GroovyLanguage.INSTANCE);
    assert containingFile != null : element.getContainingFile();
    ASTNode astNode = containingFile.getNode();
    assert astNode != null;
    CommonCodeStyleSettings groovySettings = settings.getCommonSettings(GroovyLanguage.INSTANCE);
    GroovyCodeStyleSettings customSettings = settings.getCustomSettings(GroovyCodeStyleSettings.class);

    final AlignmentProvider alignments = new AlignmentProvider();
    if (customSettings.USE_FLYING_GEESE_BRACES) {
      element.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (GeeseUtil.isClosureRBrace(element)) {
            GeeseUtil.calculateRBraceAlignment(element, alignments);
          }
          else {
            super.visitElement(element);
          }
        }
      });
    }
    final GroovyBlock block = new GroovyBlock(astNode, Indent.getAbsoluteNoneIndent(), null, new FormattingContext(groovySettings, alignments, customSettings, false));
    if (Registry.is("groovy.document.based.formatting")) {
      return new DocumentBasedFormattingModel(block, settings, containingFile);
    }
    else {
      return new GroovyFormattingModel(containingFile, block, FormattingDocumentModelImpl.createOn(containingFile));
    }
  }

  /**
   * Standard {@link PsiBasedFormattingModel} extension that handles the fact that groovy uses not single white space token type
   * ({@link TokenType#WHITE_SPACE}) but one additional token type as well: {@link GroovyTokenTypes#mNLS}. So, it allows to adjust
   * white space token type to use for calling existing common formatting stuff.
   */
  private static class GroovyFormattingModel extends PsiBasedFormattingModel {

    GroovyFormattingModel(PsiFile file, @NotNull Block rootBlock, FormattingDocumentModelImpl documentModel) {
      super(file, rootBlock, documentModel);
    }

    @Override
    protected String replaceWithPsiInLeaf(TextRange textRange, String whiteSpace, ASTNode leafElement) {
      if (!myCanModifyAllWhiteSpaces) {
        if (PsiImplUtil.isWhiteSpaceOrNls(leafElement)) return null;
      }

      IElementType elementTypeToUse = TokenType.WHITE_SPACE;
      ASTNode prevNode = TreeUtil.prevLeaf(leafElement);
      if (prevNode != null && PsiImplUtil.isWhiteSpaceOrNls(prevNode)) {
        elementTypeToUse = prevNode.getElementType();
      }
      FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, elementTypeToUse, textRange);
      return whiteSpace;
    }
  }
}
