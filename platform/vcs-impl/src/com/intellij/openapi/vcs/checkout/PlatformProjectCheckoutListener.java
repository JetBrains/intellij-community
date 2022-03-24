// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Open directory.
 */
final class PlatformProjectCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(@NotNull Project project, @NotNull Path directory) {
    return ProjectUtil.openOrImport(directory, OpenProjectTask.build().withProjectToClose(project)) != null;
  }
}
