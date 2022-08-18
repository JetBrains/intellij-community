// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface PsiViewerExtension {

  ExtensionPointName<PsiViewerExtension> EP_NAME = ExtensionPointName.create("com.intellij.dev.psiViewer.extension");

  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getName();

  @NotNull
  Icon getIcon();

  @NotNull
  PsiElement createElement(@NotNull Project project, @NotNull String text);

  @NotNull
  FileType getDefaultFileType();
}
