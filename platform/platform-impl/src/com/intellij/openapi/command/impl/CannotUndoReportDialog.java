// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

@ApiStatus.Internal
public final class CannotUndoReportDialog extends DialogWrapper implements UiDataProvider {
  private static final int FILE_TEXT_PREVIEW_CHARS_LIMIT = 40;
  private final Project myProject;

  private JList<DocumentReference> myProblemFilesList;
  private JPanel myPanel;
  private JLabel myProblemMessageLabel;

  public CannotUndoReportDialog(Project project, @Nls String problemText, Collection<? extends DocumentReference> files) {
    super(project, false);
    myProject = project;

    DefaultListModel<DocumentReference> model = new DefaultListModel<>();
    for (DocumentReference file : files) {
      model.addElement(file);
    }
    myProblemFilesList.setCellRenderer(new SimpleListCellRenderer<>() {
      @Override
      public void customize(@NotNull JList<? extends DocumentReference> list,
                            DocumentReference file,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        final VirtualFile vFile = file.getFile();
        if (vFile != null) {
          setText(vFile.getPresentableUrl());
        }
        else {
          Document document = file.getDocument();
          CharSequence content = document == null ? null : document.getImmutableCharSequence();
          if (content != null && content.length() > FILE_TEXT_PREVIEW_CHARS_LIMIT) {
            content = content.subSequence(0, FILE_TEXT_PREVIEW_CHARS_LIMIT) + "...";
          }
          setText(IdeBundle.message("list.item.temporary.file.0", content == null ? "" : " [" + content + "]"));
        }
      }
    });

    myProblemFilesList.setModel(model);
    EditSourceOnDoubleClickHandler.install(myProblemFilesList, () -> doOKAction());
    EditSourceOnEnterKeyHandler.install(myProblemFilesList, () -> doOKAction());
    setTitle(IdeBundle.message("cannot.undo.title"));

    myProblemMessageLabel.setText(problemText);
    myProblemMessageLabel.setIcon(Messages.getErrorIcon());

    init();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      DocumentReference value = myProblemFilesList.getSelectedValue();
      VirtualFile file = value != null ? value.getFile() : null;
      return file != null ? new OpenFileDescriptor(myProject, file) : null;
    });
  }
}