// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

public final class JavaFxMainMethodRunConfigurationProvider implements JavaMainMethodProvider {
  public static final @NonNls String LAUNCH_MAIN = "launch";

  @Override
  public boolean isApplicable(@NotNull PsiClass clazz) {
    return !DumbService.isDumb(clazz.getProject()) &&
           InheritanceUtil.isInheritor(clazz, true, JavaFxCommonNames.JAVAFX_APPLICATION_APPLICATION);
  }

  @Override
  public boolean hasMainMethod(@NotNull PsiClass clazz) {
    return isApplicable(clazz);
  }

  @Override
  public @Nullable PsiMethod findMainInClass(@NotNull PsiClass clazz) {
    final PsiMethod[] launches = clazz.findMethodsByName(LAUNCH_MAIN, true);
    for (PsiMethod launchMethod : launches) {
      if (PsiMethodUtil.isMainMethod(launchMethod)) {
        return launchMethod;
      }
    }
    return null;
  }
}
