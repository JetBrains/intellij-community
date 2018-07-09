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
package org.jetbrains.idea.devkit.inspections;


import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;
import org.jetbrains.idea.devkit.util.PsiUtil;

abstract class DescriptionNotFoundInspectionBase extends DevKitInspectionBase {

  private final DescriptionType myDescriptionType;

  protected DescriptionNotFoundInspectionBase(DescriptionType descriptionType) {
    myDescriptionType = descriptionType;
  }

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Project project = aClass.getProject();
    final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    final Module module = ModuleUtilCore.findModuleForPsiElement(aClass);

    if (nameIdentifier == null || module == null || !PsiUtil.isInstantiable(aClass)) return null;

    final PsiClass base = JavaPsiFacade.getInstance(project).findClass(getClassName(), aClass.getResolveScope());
    if (base == null || !aClass.isInheritor(base, true)) return null;

    String descriptionDir = DescriptionCheckerUtil.getDescriptionDirName(aClass);
    if (StringUtil.isEmptyOrSpaces(descriptionDir)) {
      return null;
    }

    for (PsiDirectory description : getDescriptionsDirs(module)) {
      PsiDirectory dir = description.findSubdirectory(descriptionDir);
      if (dir == null) continue;
      final PsiFile descr = dir.findFile("description.html");
      if (descr != null) {
        if (!hasBeforeAndAfterTemplate(dir.getVirtualFile())) {
          PsiElement problem = aClass.getNameIdentifier();
          ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(problem == null ? nameIdentifier : problem,
                                                                                getHasNotBeforeAfterError(),
                                                                                isOnTheFly,
                                                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
          return new ProblemDescriptor[]{problemDescriptor};
        }

        return null;
      }
    }


    final PsiElement problem = aClass.getNameIdentifier();
    final ProblemDescriptor problemDescriptor = manager
      .createProblemDescriptor(problem == null ? nameIdentifier : problem,
                               getHasNotDescriptionError(), isOnTheFly, new LocalQuickFix[]{getFix(module, descriptionDir)},
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    return new ProblemDescriptor[]{problemDescriptor};
  }

  protected CreateHtmlDescriptionFix getFix(Module module, String descriptionDir) {
    return new CreateHtmlDescriptionFix(descriptionDir, module, myDescriptionType);
  }

  private static boolean hasBeforeAndAfterTemplate(@NotNull VirtualFile dir) {
    boolean hasBefore = false;
    boolean hasAfter = false;

    for (VirtualFile file : dir.getChildren()) {
      String name = file.getName();
      if (name.endsWith(".template")) {
        if (name.startsWith("before.")) {
          hasBefore = true;
        }
        else if (name.startsWith("after.")) {
          hasAfter = true;
        }
      }
    }

    return hasBefore && hasAfter;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  protected String getClassName() {
    return myDescriptionType.getClassName();
  }

  @NotNull
  protected PsiDirectory[] getDescriptionsDirs(@NotNull Module module) {
    return DescriptionCheckerUtil.getDescriptionsDirs(module, myDescriptionType);
  }

  @NotNull
  protected abstract String getHasNotDescriptionError();

  @NotNull
  protected abstract String getHasNotBeforeAfterError();
}
