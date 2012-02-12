/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author swr
 */
abstract class BaseFix implements LocalQuickFix {
  protected final PsiElement myElement;
  protected final boolean myOnTheFly;

  protected BaseFix(PsiElement element, boolean onTheFly) {
    myElement = element;
    myOnTheFly = onTheFly;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    // can happen during batch-inspection if resolution has already been applied
    // to plugin.xml or java class
    if (!myElement.isValid()) return;

    final boolean external = descriptor.getPsiElement().getContainingFile() != myElement.getContainingFile();
    if (external) {
      final PsiClass clazz = PsiTreeUtil.getParentOfType(myElement, PsiClass.class, false);
      final ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
      final VirtualFile[] files = new VirtualFile[]{myElement.getContainingFile().getVirtualFile()};
      final ReadonlyStatusHandler.OperationStatus status = readonlyStatusHandler.ensureFilesWritable(files);

      if (status.hasReadonlyFiles()) {
        final String className = clazz != null ? clazz.getQualifiedName() : myElement.getContainingFile().getName();

        Messages.showMessageDialog(project,
                DevKitBundle.message("inspections.registration.problems.quickfix.read-only",
                        className),
                getName(),
                Messages.getErrorIcon());
        return;
      }
    }

    try {
      doFix(project, descriptor, external);
    }
    catch (IncorrectOperationException e) {
      Logger.getInstance("#" + getClass().getName()).error(e);
    }
  }

  protected abstract void doFix(Project project, ProblemDescriptor descriptor, boolean external) throws IncorrectOperationException;
}
