/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
    myTextEditor.setText(initialText);
    myTextEditor.getEditorField().setCaretPosition(0);
    init();
  }

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
