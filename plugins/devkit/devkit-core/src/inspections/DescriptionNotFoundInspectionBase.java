// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHolderUtilKt;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;

abstract class DescriptionNotFoundInspectionBase extends DevKitUastInspectionBase {

  private final DescriptionType myDescriptionType;

  protected DescriptionNotFoundInspectionBase(DescriptionType descriptionType) {
    super(UClass.class);
    myDescriptionType = descriptionType;
  }

  @Override
  public final ProblemDescriptor @Nullable [] checkClass(@NotNull UClass uClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (uClass instanceof UAnonymousClass) return null;

    PsiClass psiClass = uClass.getJavaPsi();
    final PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (nameIdentifier == null || module == null || !PsiUtil.isInstantiable(psiClass)) return null;

    if (!myDescriptionType.matches(psiClass)) return null;

    if (skipIfNotRegistered(psiClass)) {
      return null;
    }

    ProblemsHolder holder = new ProblemsHolder(manager, psiClass.getContainingFile(), isOnTheFly);
    boolean registered;
    if (myDescriptionType.isFixedDescriptionFilename()) {
      registered = checkFixedDescription(holder, module, psiClass, uClass);
    }
    else {
      registered = checkDynamicDescription(holder, module, psiClass);
    }

    if (registered) return holder.getResultsArray();

    ProblemHolderUtilKt.registerUProblem(holder, uClass, getHasNotDescriptionError(module, psiClass),
                                         new CreateHtmlDescriptionFix(getDescriptionDir(module, psiClass), module, myDescriptionType));
    return holder.getResultsArray();
  }

  protected abstract boolean skipIfNotRegistered(PsiClass epClass);

  protected boolean skipOptionalBeforeAfter(PsiClass epClass) {
    return false;
  }

  protected boolean checkDynamicDescription(ProblemsHolder holder, Module module, PsiClass psiClass) {
    throw new IllegalStateException("must be implemented for " + getClass());
  }

  protected boolean checkFixedDescription(ProblemsHolder holder,
                                          Module module,
                                          PsiClass psiClass,
                                          UClass uClass) {
    String descriptionDir = getDescriptionDir(module, psiClass);
    if (StringUtil.isEmptyOrSpaces(descriptionDir)) {
      return false;
    }

    for (PsiDirectory description : getDescriptionsDirs(module)) {
      PsiDirectory dir = description.findSubdirectory(descriptionDir);
      if (dir == null) continue;
      final PsiFile descr = dir.findFile("description.html");
      if (descr == null) continue;

      if (!hasBeforeAndAfterTemplate(dir.getVirtualFile()) &&
          !skipOptionalBeforeAfter(psiClass)) {
        ProblemHolderUtilKt.registerUProblem(holder, uClass, getHasNotBeforeAfterError());
      }
      return true;
    }
    return false;
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

  @Nullable
  protected String getDescriptionDir(Module module, PsiClass psiClass) {
    return DescriptionCheckerUtil.getDescriptionDirName(psiClass);
  }

  protected PsiDirectory @NotNull [] getDescriptionsDirs(Module module) {
    return DescriptionCheckerUtil.getDescriptionsDirs(module, myDescriptionType);
  }

  @InspectionMessage
  @NotNull
  protected abstract String getHasNotDescriptionError(Module module, PsiClass psiClass);

  @InspectionMessage
  @NotNull
  protected abstract String getHasNotBeforeAfterError();
}
