// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class ChangeListViewerDialog extends DialogWrapper {
  private final CommittedChangeListPanel myChangesPanel;

  public ChangeListViewerDialog(@NotNull Project project, @NotNull CommittedChangeList changeList, @Nullable VirtualFile toSelect) {
    this(project, null, changeList, toSelect);
  }

  public ChangeListViewerDialog(@NotNull Project project, @NotNull Collection<Change> changes) {
    this(null, project, changes);
  }

  public ChangeListViewerDialog(@Nullable Component parent, @NotNull Project project, @NotNull Collection<Change> changes) {
    this(project, parent, CommittedChangeListPanel.createChangeList(changes), null);
    myChangesPanel.setShowCommitMessage(false);
  }

  private ChangeListViewerDialog(@NotNull Project project,
                                 @Nullable Component parentComponent,
                                 @NotNull CommittedChangeList changeList,
                                 @Nullable VirtualFile toSelect) {
    super(project, parentComponent, true, IdeModalityType.IDE);

    myChangesPanel = new CommittedChangeListPanel(project);
    myChangesPanel.setChangeList(changeList, toSelect);
    myChangesPanel.setDescription(getDescription());

    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.message("close.action.name"));
    setModal(false);

    init();
  }

  /**
   * @param inAir true if changes are not related to known VCS roots (ex: local changes, file history, etc)
   */
  public void markChangesInAir(boolean inAir) {
    myChangesPanel.getChangesBrowser().setUseCase(inAir ? CommittedChangesBrowserUseCase.IN_AIR : null);
  }


  @Override
  protected String getDimensionServiceKey() {
    return "VCS.ChangeListViewerDialog";
  }

  @Override
  public JComponent createCenterPanel() {
    return myChangesPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    Action cancelAction = getCancelAction();
    cancelAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    return new Action[]{cancelAction};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangesPanel.getPreferredFocusedComponent();
  }

  /**
   * @return description that is added to the top of this dialog. May be null - then no description is shown.
   */
  @Nullable
  protected String getDescription() {
    return null;
  }
}
