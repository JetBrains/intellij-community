// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * Open project with {@code project.ipr}.
 */
public class ProjectCheckoutListener implements CheckoutListener {
  public ProjectCheckoutListener() { }

  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File[] files = directory.listFiles((dir, name) -> dir.isFile() && name.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION));
    if (files != null && files.length > 0) {
      ProjectUtil.openProject(files[0].getPath(), project, false);
      return true;
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) { }
}