// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

public abstract class CommittedChangesPanel extends JPanel implements DataProvider, Disposable {
  @NotNull protected final CommittedChangesTreeBrowser myBrowser;
  @NotNull protected final Project myProject;

  public CommittedChangesPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myBrowser = new CommittedChangesTreeBrowser(project, new ArrayList<>());
    Disposer.register(this, myBrowser);
  }

  protected void setup(@Nullable ActionGroup extraActions, @Nullable VcsCommittedViewAuxiliary auxiliary) {
    add(myBrowser, BorderLayout.CENTER);

    JPanel toolbarPanel = new JPanel();
    toolbarPanel.setLayout(new BoxLayout(toolbarPanel, BoxLayout.X_AXIS));

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("CommittedChangesToolbar");

    ActionToolbar toolBar = myBrowser.createGroupFilterToolbar(myProject, group, extraActions,
                                                               auxiliary != null ? auxiliary.getToolbarActions() : Collections.emptyList());
    CommittedChangesFilterComponent filterComponent = new CommittedChangesFilterComponent();
    Disposer.register(this, filterComponent);

    toolbarPanel.add(toolBar.getComponent());
    toolbarPanel.add(Box.createHorizontalGlue());
    toolbarPanel.add(filterComponent);
    filterComponent.setMinimumSize(filterComponent.getPreferredSize());
    filterComponent.setMaximumSize(filterComponent.getPreferredSize());
    myBrowser.setToolBar(toolbarPanel);

    if (auxiliary != null) {
      Disposer.register(this, () -> auxiliary.getCalledOnViewDispose());
      myBrowser.setTableContextMenu(group, auxiliary.getPopupActions());
    }
    else {
      myBrowser.setTableContextMenu(group, Collections.emptyList());
    }

    EmptyAction.registerWithShortcutSet("CommittedChanges.Refresh", CommonShortcuts.getRerun(), this);
    myBrowser.addFilter(filterComponent);
  }

  public abstract void refreshChanges();

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return myBrowser.getData(dataId);
  }

  @Override
  public void dispose() {
  }
}
