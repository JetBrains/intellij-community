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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;

public class GrSynchronizedFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    GrSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(psiElement, GrSynchronizedStatement.class);
    if (synchronizedStatement == null || synchronizedStatement.getBody() != null) return;

    if (!PsiTreeUtil.isAncestor(synchronizedStatement.getMonitor(), psiElement, false)) return;


    final Document doc = editor.getDocument();

    PsiElement eltToInsertAfter = synchronizedStatement.getRParenth();
    String text = "{\n}";
    if (eltToInsertAfter == null) {
      eltToInsertAfter = synchronizedStatement.getMonitor();
      text = "){\n}";
    }

    doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
  }
}
