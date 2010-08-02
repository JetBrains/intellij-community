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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class ConvertStringToGStringIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new StringLiteralPredicate();
  }

  public void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrLiteral exp = (GrLiteral)element;
    final String textString = exp.getText();

    IntentionUtils.replaceExpression(convertStringLiteralToGStringLiteral(textString), exp);
  }

  private static String convertStringLiteralToGStringLiteral(String stringLiteral) {
    final String contents;
    final String delimiter;
    if (stringLiteral.startsWith("'''")) {
      contents = stringLiteral.substring(3, stringLiteral.length() - 3);
    }
    else {
      contents = stringLiteral.substring(1, stringLiteral.length() - 1);
    }
    final String escaped = escape(contents);
    if (escaped.contains("\n")) {
      delimiter = "\"\"\"";
    }
    else {
      delimiter = "\"";
    }
    return delimiter + escaped + delimiter;
  }

  private static String escape(String contents) {
    final StringBuilder out = new StringBuilder();
    final char[] chars = contents.toCharArray();

    final int len = chars.length - 1;
    int i;
    for (i = 0; i < len; i++) {
      if (chars[i] == '\\') {
        if (chars[i + 1] == '\'') {
          i++;
          out.append('\'');
        }
        else if (chars[i + 1] == 'n') {
          out.append('\n');
          i++;
        }
        else {
          out.append("\\");
          i++;
          out.append(chars[i]);
        }
        continue;
      }
      if (chars[i] == '$' || chars[i] == '"') {
        out.append('\\');
      }
      out.append(chars[i]);
    }
    if (i == len) {
      if (chars[i] == '$' || chars[i] == '"') {
        out.append('\\');
      }
      out.append(chars[i]);
    }
    return out.toString();
  }
}
