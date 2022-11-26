// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiViewer;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class PsiViewerMethodExtension extends JavaPsiViewerExtension {
  @Override
  public @NotNull String getName() {
    return "Java Method";
  }

  @Override
  public @NotNull Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Method);
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull Project project, @NotNull String text) {
    return getFactory(project).createMethodFromText(text, null);
  }
}
