// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The dialog used as a genera purpose editor requested by Git via the GIT_EDITOR environment variable.
 * Usually it is shown to edit a commit message after choosing reword or squash interactive rebase actions.
 */
public class GitUnstructuredEditor extends DialogWrapper {
  @Nullable private final JBLabel myRootLabel;
  @NotNull private final CommitMessage myTextEditor;

  public GitUnstructuredEditor(@NotNull Project project,
                               @Nullable VirtualFile root,
                               @NotNull @NonNls String initialText,
                               @NotNull @NlsContexts.DialogTitle String dialogTitle,
                               @NotNull @NlsContexts.Button String okButtonText) {
    super(project, true);
    setTitle(dialogTitle);
    setOKButtonText(okButtonText);

    myRootLabel = root == null
                  ? null
                  : new JBLabel(GitBundle.message("rebase.interactive.unstructured.editor.dialog.root.label", root.getPresentableUrl()));

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
    if (myRootLabel != null) {
      rootPanel.addToTop(myRootLabel);
    }
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
  public String getText() {
    return myTextEditor.getComment();
  }
}
