/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public class ConvertToRegexIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    if (!(element instanceof GrLiteral)) return;


    StringBuilder buffer = new StringBuilder();
    buffer.append("/");

    if (GrStringUtil.isDollarSlashyString(((GrLiteral)element))) {
      buffer.append(GrStringUtil.removeQuotes(element.getText()));
    }
    else if (element instanceof GrLiteralImpl) {
      Object value = ((GrLiteralImpl)element).getValue();
      if (value instanceof String) {
        GrStringUtil.escapeSymbolsForSlashyStrings(buffer, (String)value);
      }
      else {
        String rawText = GrStringUtil.removeQuotes(element.getText());
        unescapeAndAppend(buffer, rawText);
      }
    }
    else if (element instanceof GrString) {
      for (PsiElement part : ((GrString)element).getAllContentParts()) {
        if (part instanceof GrStringContent) {
          unescapeAndAppend(buffer, part.getText());
        }
        else if (part instanceof GrStringInjection) {
          buffer.append(part.getText());
        }
      }
    }

    buffer.append("/");
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrExpression regex = factory.createExpressionFromText(buffer);

    element.replace(regex); //don't use replaceWithExpression since it can revert regex to string if regex brakes syntax
  }

  private static void unescapeAndAppend(StringBuilder buffer, String rawText) {
    String parsed = GrStringUtil.unescapeString(rawText);
    GrStringUtil.escapeSymbolsForSlashyStrings(buffer, parsed);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrLiteral &&
               GrStringUtil.isStringLiteral((GrLiteral)element) &&
               !GrStringUtil.removeQuotes(element.getText()).isEmpty() &&
               !GrStringUtil.isSlashyString(((GrLiteral)element));
      }
    };
  }
}
