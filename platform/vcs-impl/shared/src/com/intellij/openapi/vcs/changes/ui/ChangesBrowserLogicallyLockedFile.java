// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.LogicalLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

@ApiStatus.Internal
public class ChangesBrowserLogicallyLockedFile extends ChangesBrowserFileNode {
  private final LogicalLock myLogicalLock;

  public ChangesBrowserLogicallyLockedFile(@Nullable Project project, VirtualFile userObject, LogicalLock logicalLock) {
    super(project, userObject);
    myLogicalLock = logicalLock;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    String lockedBy = VcsBundle.message("changes.locked.by", myLogicalLock.getOwner());
    renderer.append(spaceAndThinSpace() + lockedBy, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}
