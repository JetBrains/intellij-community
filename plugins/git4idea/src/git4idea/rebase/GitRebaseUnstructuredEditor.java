// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The dialog used for the unstructured information from git rebase,
 * usually the commit message after choosing reword or squash interactive rebase actions.
 */
class GitRebaseUnstructuredEditor extends DialogWrapper {
  @NotNull private final JBLabel myRootLabel;
  @NotNull private final CommitMessage myTextEditor;

  GitRebaseUnstructuredEditor(@NotNull Project project, @NotNull VirtualFile root, @NotNull String initialText) {
    super(project, true);
    setTitle(GitBundle.message("rebase.unstructured.editor.title"));
    setOKButtonText(GitBundle.message("rebase.unstructured.editor.button"));

    myRootLabel = new JBLabel("Git Root: " + root.getPresentableUrl());

    myTextEditor = new CommitMessage(project, false, false, false);
    Disposer.register(getDisposable(), myTextEditor);
    myTextEditor.setText(initialText);
    myTextEditor.getEditorField().setCaretPosition(0);
    init();
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    BorderLayoutPanel rootPanel = JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP);
    rootPanel.addToTop(myRootLabel);
    rootPanel.addToCenter(myTextEditor);
    return rootPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTextEditor.getEditorField().getFocusTarget();
  }

  @NotNull
  String getText() {
    return myTextEditor.getComment();
  }
}
