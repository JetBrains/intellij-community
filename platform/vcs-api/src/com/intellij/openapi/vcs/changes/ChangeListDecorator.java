// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Allows to modify the painting of changelists in the Changes view.
 */
public interface ChangeListDecorator {
  ProjectExtensionPointName<ChangeListDecorator> EP_NAME = new ProjectExtensionPointName<>("com.intellij.vcs.changeListDecorator");

  @NotNull
  static List<ChangeListDecorator> getDecorators(@NotNull Project project) {
    return project.isDisposed() ? Collections.emptyList() : EP_NAME.getExtensions(project);
  }

  void decorateChangeList(@NotNull LocalChangeList changeList, @NotNull ColoredTreeCellRenderer cellRenderer,
                          boolean selected, boolean expanded, boolean hasFocus);
}
