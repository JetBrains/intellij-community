// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
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
        if (!skipIfNotRegistered(aClass) &&
            !hasBeforeAndAfterTemplate(dir.getVirtualFile())) {
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

    if (skipIfNotRegistered(aClass)) {
      return null;
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

  protected abstract boolean skipIfNotRegistered(PsiClass epClass);

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

  @NotNull
  protected String getClassName() {
    return myDescriptionType.getClassName();
  }

  protected PsiDirectory @NotNull [] getDescriptionsDirs(@NotNull Module module) {
    return DescriptionCheckerUtil.getDescriptionsDirs(module, myDescriptionType);
  }

  @InspectionMessage
  @NotNull
  protected abstract String getHasNotDescriptionError();

  @InspectionMessage
  @NotNull
  protected abstract String getHasNotBeforeAfterError();
}
