/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;

public class NonProjectFileNotificationPanel extends EditorNotificationPanel {
  private final HyperlinkLabel myUnlockAction;
  private final HyperlinkLabel myUnlockAllLabel;

  public NonProjectFileNotificationPanel(@NotNull final Project project, @NotNull final VirtualFile file) {
    setText("This file is not in the project, please unlock it to continue editing");

    myUnlockAction = createActionLabel("Unlock", new Runnable() {
      @Override
      public void run() {
        NonProjectFileWritingAccessProvider.allowAccess(project, file);
      }
    });
    myUnlockAllLabel = createActionLabel("Unlock all in current session", new Runnable() {
      @Override
      public void run() {
        NonProjectFileWritingAccessProvider.allowAccessForAll(project, file);
      }
    });
  }

  @NotNull
  public HyperlinkLabel getUnlockAction() {
    return myUnlockAction;
  }

  @NotNull
  public HyperlinkLabel getUnlockAllLabel() {
    return myUnlockAllLabel;
  }
}
