/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.devkit.DevKitBundle;

public class MakePublicFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.devkit.inspections.quickfix.MakePublicFix");

  private final PsiModifierListOwner myElement;
  private final boolean myMakeReadable;

  public MakePublicFix(PsiModifierListOwner checkedElement, boolean makeReadable) {
    myElement = checkedElement;
    myMakeReadable = makeReadable;
  }

  public String getName() {
    return DevKitBundle.message("inspections.registration.problems.quickfix.make.public",
            myElement.getLanguage().getFindUsagesProvider().getType(myElement));
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    if (myMakeReadable) {
      final ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
      final VirtualFile[] files = new VirtualFile[]{myElement.getContainingFile().getVirtualFile()};
      final ReadonlyStatusHandler.OperationStatus status = readonlyStatusHandler.ensureFilesWritable(files);

      if (status.hasReadonlyFiles()) {
        final String className = myElement instanceof PsiClass ?
                ((PsiClass)myElement).getQualifiedName() :
                myElement instanceof PsiMember ?
                        ((PsiMember)myElement).getContainingClass().getQualifiedName() :
                        null;
        assert className != null;

        Messages.showMessageDialog(project,
                DevKitBundle.message("inspections.registration.problems.quickfix.make.public.read-only",
                        className),
                getName(),
                Messages.getErrorIcon());
        return;
      }
    }

    try {
      myElement.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
    }
    catch (IncorrectOperationException e) {
      // Display an error message instead of causing an "internal error"?
      // Setting a modifier should never fail, should it?
      LOG.error(e);
    }
  }

  public String getFamilyName() {
    return getName();
  }
}
