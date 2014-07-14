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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.unscramble.UnscrambleDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class InvestigateFix implements IntentionAction {
  private final String myReason;

  public InvestigateFix(String reason) {
    myReason = reason;
  }

  static void analyzeStackTrace(Project project, String exceptionText) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.setText(exceptionText);
    dialog.show();
  }

  @NotNull
  @Override
  public String getText() {
    return "View details";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Investigate DSL descriptor processing error";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    analyzeStackTrace(project, myReason);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
