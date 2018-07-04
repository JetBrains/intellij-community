// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
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
  private static final int FILE_TEXT_PREVIEW_CHARS_LIMIT = 40;

  private JList<String> myProblemFilesList;
  private JPanel myPanel;
  private JLabel myProblemMessageLabel;

  public CannotUndoReportDialog(Project project, @Nls String problemText, Collection<DocumentReference> files) {
    super(project, false);

    DefaultListModel<String> model = new DefaultListModel<>();
    for (DocumentReference file : files) {
      final VirtualFile vFile = file.getFile();
      if (vFile != null) {
        model.add(0, vFile.getPresentableUrl());
      }
      else {
        Document document = file.getDocument();
        CharSequence content = document == null ? null : document.getImmutableCharSequence();
        if (content != null && content.length() > FILE_TEXT_PREVIEW_CHARS_LIMIT) {
          content = content.subSequence(0, FILE_TEXT_PREVIEW_CHARS_LIMIT) + "...";
        }
        model.add(0, "<temporary file>" + (content == null ? "" : " [" + content + "]"));
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
