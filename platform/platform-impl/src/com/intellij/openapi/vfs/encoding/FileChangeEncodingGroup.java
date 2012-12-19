/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.status.EncodingActionsPair;
import com.intellij.pom.Navigatable;

/**
 * @author cdr
 */
public class FileChangeEncodingGroup extends DefaultActionGroup implements DumbAware {
  private boolean myUpdating;

  private final EncodingActionsPair encodingActionsPair = new EncodingActionsPair();

  @Override
  public void update(AnActionEvent e) {
    if (myUpdating) {
      return;
    }
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 1) {
      virtualFile = null;
    }
    if (virtualFile != null) {
      Navigatable navigatable = e.getData(PlatformDataKeys.NAVIGATABLE);
      if (navigatable instanceof OpenFileDescriptor) {
        // prefer source to the class file
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    if (virtualFile != null && !virtualFile.isInLocalFileSystem()) {
      virtualFile = null;
    }

    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    boolean enabled =
      encodingActionsPair.areActionsEnabled(null, editor, editor == null ? null : editor.getComponent(), virtualFile, getEventProject(e));
    removeAll();
    if (enabled) {
      addAll(encodingActionsPair.createActionGroup());
    }

    myUpdating = true;
    try {
      e.getPresentation().setText("File encoding");
      // updating the enabled state of the action can trigger the menuSelected handler, which updates the action group again
      e.getPresentation().setEnabled(enabled);
    }
    finally {
      myUpdating = false;
    }
  }
}
