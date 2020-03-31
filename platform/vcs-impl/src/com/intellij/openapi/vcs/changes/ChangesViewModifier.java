// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * This extension point provides a plugin to modify parts of Local Changes.
 */
public interface ChangesViewModifier {
  ProjectExtensionPointName<ChangesViewModifier> KEY = new ProjectExtensionPointName<>("com.intellij.vcs.changes.changesViewModifier");

  default void modifyTreeModelBuilder(@NotNull ChangesViewModelBuilder builder) { }
}
