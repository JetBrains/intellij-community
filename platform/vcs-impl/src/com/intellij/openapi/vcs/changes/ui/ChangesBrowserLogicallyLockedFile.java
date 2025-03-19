/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
