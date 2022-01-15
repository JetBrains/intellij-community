// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.ui.browser.LoadingChangesPanel;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class LoadingCommittedChangeListPanel implements Disposable {
  private final CommittedChangeListPanel myChangesPanel;
  private final LoadingChangesPanel myLoadingPanel;

  public LoadingCommittedChangeListPanel(@NotNull Project project) {
    myChangesPanel = new CommittedChangeListPanel(project);

    StatusText emptyText = myChangesPanel.getChangesBrowser().getViewer().getEmptyText();
    myLoadingPanel = new LoadingChangesPanel(myChangesPanel, emptyText, this);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public JComponent getContent() {
    return myLoadingPanel;
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myChangesPanel.getPreferredFocusedComponent();
  }

  @NotNull
  public SimpleChangesBrowser getChangesBrowser() {
    return myChangesPanel.getChangesBrowser();
  }

  public void hideCommitMessage() {
    myChangesPanel.setShowCommitMessage(false);
  }

  public void hideSideBorders() {
    myChangesPanel.setShowSideBorders(false);
  }

  /**
   * @param inAir true if changes are not related to known VCS roots (ex: local changes, file history, etc)
   */
  public void markChangesInAir(boolean inAir) {
    myChangesPanel.getChangesBrowser().setUseCase(inAir ? CommittedChangesBrowserUseCase.IN_AIR : null);
  }

  /**
   * @param description Text that is added to the top of this dialog. May be null - then no description is shown.
   */
  public void setDescription(@Nullable @NlsContexts.Label String description) {
    myChangesPanel.setDescription(description);
  }

  public void setChanges(@NotNull CommittedChangeList changeList, @Nullable VirtualFile toSelect) {
    myChangesPanel.setChangeList(changeList);
    myChangesPanel.getChangesBrowser().getViewer().selectFile(toSelect);
  }

  public void setChanges(@NotNull Collection<Change> changes, @Nullable VirtualFile toSelect) {
    hideCommitMessage();
    myChangesPanel.setChangeList(CommittedChangeListPanel.createChangeList(changes));
    myChangesPanel.getChangesBrowser().getViewer().selectFile(toSelect);
  }

  public void loadChangesInBackground(@NotNull ThrowableComputable<? extends ChangelistData, ? extends VcsException> computable) {
    myLoadingPanel.loadChangesInBackground(computable, (result) -> {
      if (result != null) {
        myChangesPanel.setChangeList(result.changeList);
        myChangesPanel.getChangesBrowser().getViewer().selectFile(result.toSelect);
      }
      else {
        myChangesPanel.setChangeList(CommittedChangeListPanel.createChangeList(Collections.emptySet()));
      }
    });
  }

  public static class ChangelistData {
    @NotNull public final CommittedChangeList changeList;
    @Nullable public final FilePath toSelect;

    public ChangelistData(@NotNull CommittedChangeList changeList, @Nullable FilePath toSelect) {
      this.changeList = changeList;
      this.toSelect = toSelect;
    }
  }
}
