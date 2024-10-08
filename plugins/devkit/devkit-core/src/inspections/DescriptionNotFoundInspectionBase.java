// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.jvm.DefaultJvmElementVisitor;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;

abstract class DescriptionNotFoundInspectionBase extends DevKitJvmInspection {

  private final DescriptionType myDescriptionType;

  protected DescriptionNotFoundInspectionBase(DescriptionType descriptionType) {
    myDescriptionType = descriptionType;
  }

  protected JvmElementVisitor<Boolean> buildVisitor(@NotNull Project project, @NotNull HighlightSink sink, boolean isOnTheFly) {
    return new DefaultJvmElementVisitor<>() {
      @Override
      public Boolean visitClass(@NotNull JvmClass clazz) {
        PsiElement sourceElement = clazz.getSourceElement();
        if (!(sourceElement instanceof PsiClass)) {
          return null;
        }
        checkClass((PsiClass)sourceElement, sink);
        return false;
      }
    };
  }

  private void checkClass(@NotNull PsiClass psiClass, @NotNull HighlightSink sink) {
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(psiClass)) return;
    if (!myDescriptionType.matches(psiClass)) return;

    final Module module = ModuleUtilCore.findModuleForPsiElement(psiClass);
    if (module == null) return;

    if (skipIfNotRegistered(psiClass)) {
      return;
    }

    boolean registered;
    if (myDescriptionType.isFixedDescriptionFilename()) {
      registered = checkFixedDescription(sink, module, psiClass);
    }
    else {
      registered = checkDynamicDescription(sink, module, psiClass);
    }
    if (registered) return;

    sink.highlight(getHasNotDescriptionError(module, psiClass),
                   new CreateHtmlDescriptionFix(getDescriptionDir(module, psiClass), module, myDescriptionType));
  }

  protected abstract boolean skipIfNotRegistered(PsiClass epClass);

  protected boolean skipOptionalBeforeAfter(PsiClass epClass) {
    return false;
  }

  protected boolean checkDynamicDescription(HighlightSink sink, Module module, PsiClass psiClass) {
    throw new IllegalStateException("must be implemented for " + getClass());
  }

  protected boolean checkFixedDescription(HighlightSink sink,
                                          Module module,
                                          PsiClass psiClass) {
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
        sink.highlight(getHasNotBeforeAfterError());
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
