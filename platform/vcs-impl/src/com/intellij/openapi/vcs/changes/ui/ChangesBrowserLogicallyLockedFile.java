/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.LogicalLock;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;

public class ChangesBrowserLogicallyLockedFile extends ChangesBrowserFileNode {
  private final LogicalLock myLogicalLock;

  public ChangesBrowserLogicallyLockedFile(Project project, VirtualFile userObject, LogicalLock logicalLock) {
    super(project, userObject);
    myLogicalLock = logicalLock;
  }

  @Override
  public void render(ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    super.render(renderer, selected, expanded, hasFocus);
    renderer.append(" locked by ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(myLogicalLock.getOwner(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}
