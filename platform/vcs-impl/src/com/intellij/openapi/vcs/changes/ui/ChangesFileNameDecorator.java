// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;


@ApiStatus.Internal
public abstract class ChangesFileNameDecorator {
  public static ChangesFileNameDecorator getInstance(Project project) {
    return project.getService(ChangesFileNameDecorator.class);
  }

  public abstract void appendFileName(final ChangesBrowserNodeRenderer renderer, final VirtualFile vFile, @NlsSafe String fileName,
                                      final Color color, boolean highlightProblems);
}
