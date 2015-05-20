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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.readOnlyHandler.FileListRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class NonProjectFileWritingAccessDialog extends DialogWrapper {
  private JPanel myPanel;
  private JList myFileList;
  private JRadioButton myUnlockAllButton;

  protected NonProjectFileWritingAccessDialog(@NotNull Project project, @NotNull List<VirtualFile> nonProjectFiles) {
    super(project);
    setTitle("Non-Project Files Access");

    myFileList.setCellRenderer(new FileListRenderer());
    myFileList.setModel(new CollectionListModel<VirtualFile>(nonProjectFiles));
    
    getOKAction().putValue(DEFAULT_ACTION, null);
    getCancelAction().putValue(DEFAULT_ACTION, true);
    
    init();
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public NonProjectFileWritingAccessProvider.UnlockOption getUnlockOption() {
    return myUnlockAllButton.isSelected() ? NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK_ALL
                                          : NonProjectFileWritingAccessProvider.UnlockOption.UNLOCK;
  }

  protected String getHelpId() {
    return "Non-Project_Files_Access_Dialog";
  }
}
