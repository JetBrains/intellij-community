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

package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public class CannotUndoReportDialog extends DialogWrapper {
  private JList myProblemFilesList;
  private JPanel myPanel;
  private JLabel myProblemMessageLabel;

  public CannotUndoReportDialog(Project project, @Nls String problemText, Collection<DocumentReference> files) {
    super(project, false);

    DefaultListModel model = new DefaultListModel();
    for (DocumentReference file : files) {
      final VirtualFile vFile = file.getFile();
      if (vFile != null) {
        model.add(0, vFile.getPresentableUrl());
      }
      else {
        model.add(0, "<unknown file>");
      }
    }

    myProblemFilesList.setModel(model);
    setTitle(CommonBundle.message("cannot.undo.dialog.title"));

    myProblemMessageLabel.setText(problemText);
    myProblemMessageLabel.setIcon(Messages.getErrorIcon());

    init();
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
