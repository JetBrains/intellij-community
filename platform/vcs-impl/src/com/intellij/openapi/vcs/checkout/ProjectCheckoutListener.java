// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

/**
 * Open project with {@code project.ipr}.
 */
public class ProjectCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(@NotNull Project project, @NotNull Path directory) {
    File[] files = directory.toFile().listFiles((dir, name) -> dir.isFile() && name.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION));
    if (files != null && files.length > 0) {
      ProjectUtil.openProject(files[0].toPath(), OpenProjectTask.build().withProjectToClose(project));
      return true;
    }
    return false;
  }
}
