// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Open project with {@code .idea}.
 */
final class ProjectDirCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(@NotNull Project project, @NotNull Path directory) {
    Path dotIdea = directory.resolve(Project.DIRECTORY_STORE_FOLDER);
    // todo Rider project layout - several.idea.solution-name names
    if (!Files.exists(dotIdea)) {
      return false;
    }

    ProjectManagerEx.getInstanceEx().openProject(directory, OpenProjectTask.build().withProjectToClose(project));
    return true;
  }
}
