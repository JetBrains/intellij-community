/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.08.2008
 */
public class GrLiteralFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    if (psiElement instanceof GrLiteral) {
      String text = psiElement.getText();
      if (StringUtil.startsWith(text, "'''")) {
        if (!StringUtil.endsWith(text, "'''")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "'''");
        }
      }
      else if (StringUtil.startsWith(text, "'")) {
        if (!StringUtil.endsWith(text, "'")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "'");
        }
      }
      else if (StringUtil.startsWith(text, "\"\"\"")) {
        if (!StringUtil.endsWith(text, "\"\"\"")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"\"\"");
        }
      }
      else if (StringUtil.startsWith(text, "\"")) {
        if (!StringUtil.endsWith(text, "\"")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
        }
      }
    }
  }
}
