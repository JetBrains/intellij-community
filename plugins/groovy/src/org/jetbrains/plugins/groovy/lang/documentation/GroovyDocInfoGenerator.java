// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.*;

public class GroovyDocInfoGenerator extends JavaDocInfoGenerator {

  public GroovyDocInfoGenerator(PsiElement element) {
    super(element.getProject(), element);
  }

  @Override
  protected void collectElementText(StringBuilder buffer, PsiElement element) {
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
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
