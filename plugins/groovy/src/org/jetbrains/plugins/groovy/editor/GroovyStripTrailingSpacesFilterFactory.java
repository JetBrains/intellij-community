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
package org.jetbrains.plugins.groovy.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

public class GroovyStripTrailingSpacesFilterFactory extends PsiBasedStripTrailingSpacesFilter.Factory {
  @NotNull
  @Override
  protected PsiBasedStripTrailingSpacesFilter createFilter(@NotNull Document document) {
    return new GroovyStripTrailingSpacesFilter(document);
  }

  @Override
  protected boolean isApplicableTo(@NotNull Language language) {
    return language.is(GroovyLanguage.INSTANCE);
  }
  
  private static class GroovyStripTrailingSpacesFilter extends PsiBasedStripTrailingSpacesFilter {

    protected GroovyStripTrailingSpacesFilter(@NotNull Document document) {
      super(document);
    }

    @Override
    protected void process(@NotNull PsiFile psiFile) {
      psiFile.accept(new GroovyPsiElementVisitor(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitGStringExpression(GrString gstring) {
          disableRange(gstring.getTextRange(), false);
        }

        @Override
        public void visitLiteralExpression(GrLiteral literal) {
          disableRange(literal.getTextRange(), false);
        }
      }));
    }
  } 
}
