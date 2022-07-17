// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.HoverIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EP that allows adding actions to the 'Commit' (aka 'Local Changes') tree, that are visible only when specific node is hovered by mouse.
 */
@ApiStatus.Experimental
public interface ChangesViewNodeAction {
  ProjectExtensionPointName<ChangesViewNodeAction> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.vcs.changes.changesViewNodeAction");

  @Nullable
  HoverIcon createNodeHoverIcon(@NotNull ChangesBrowserNode<?> node);
}
