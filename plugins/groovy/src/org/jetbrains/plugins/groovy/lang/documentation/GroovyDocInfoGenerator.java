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
            type == mGDOC_TAG_VALUE_TOKEN) {
          buffer.append(element.getText());
        }
      }
    });
  }
}
