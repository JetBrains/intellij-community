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
package org.jetbrains.plugins.groovy.overrideImplement.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil;

/**
 * User: Dmitry.Krasilschikov
 * Date: 17.09.2007
 */
public class ImplementMethodsQuickFix implements IntentionAction {
  private final PsiClass myPsiClass;

  public ImplementMethodsQuickFix(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("implement.methods.fix");
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("implement.methods.fix");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myPsiClass.isValid() && myPsiClass.getManager().isInProject(file);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    GroovyOverrideImplementUtil.invokeOverrideImplement(editor, file, true);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
