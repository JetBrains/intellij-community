// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.*;

public class GroovyDocInfoGenerator extends JavaDocInfoGenerator {

  public GroovyDocInfoGenerator(
    PsiElement element,
    boolean isGenerationForRenderedDoc,
    boolean doHighlightSignatures,
    boolean doHighlightCodeBlocks,
    @NotNull DocumentationSettings.InlineCodeHighlightingMode inlineCodeBlocksHighlightingMode,
    boolean doSemanticHighlightingOfLinks,
    float highlightingSaturationFactor
  ) {
    super(
      element.getProject(),
      element,
      GroovyDocHighlightingManager.getInstance(),
      isGenerationForRenderedDoc,
      doHighlightSignatures,
      doHighlightCodeBlocks,
      inlineCodeBlocksHighlightingMode,
      doSemanticHighlightingOfLinks,
      highlightingSaturationFactor);
  }

  @Override
  protected boolean isLeadingAsterisks(@Nullable PsiElement element) {
    return element != null && element.getNode().getElementType() == mGDOC_ASTERISKS;
  }

  @Override
  protected void collectElementText(StringBuilder buffer, PsiElement element) {
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        IElementType type = element.getNode().getElementType();
        if (type == mGDOC_TAG_VALUE_LPAREN ||
            type == mGDOC_TAG_VALUE_RPAREN ||
            type == mGDOC_TAG_VALUE_SHARP_TOKEN ||
            type == mGDOC_TAG_VALUE_TOKEN ||
            type == mGDOC_TAG_VALUE_COMMA ||
            type == mGDOC_COMMENT_DATA) {
          buffer.append(element.getText());
        }
      }
    });
  }
}
