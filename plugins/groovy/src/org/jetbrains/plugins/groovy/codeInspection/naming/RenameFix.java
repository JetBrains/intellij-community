/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;

public class RenameFix extends GroovyFix implements RefactoringQuickFix {

  public RenameFix() {
    super();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("intention.family.name.rename");
  }

  @Override
  public void doFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    doFix(element);
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return RefactoringActionHandlerFactory.getInstance().createRenameHandler();
  }
}
