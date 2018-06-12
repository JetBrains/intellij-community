// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.List;

class VcsLogChangeProcessor extends ChangeViewDiffRequestProcessor {
  @NotNull private final VcsLogChangesBrowser myBrowser;

  public VcsLogChangeProcessor(@NotNull Project project, @NotNull VcsLogChangesBrowser browser, @NotNull Disposable disposable) {
    super(project, DiffPlaces.VCS_LOG_VIEW);
    myBrowser = browser;
    myContentPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    Disposer.register(disposable, this);
  }

  @NotNull
  public com.intellij.ui.components.panels.Wrapper getToolbarWrapper() {
    return myToolbarWrapper;
  }

  @NotNull
  @Override
  protected List<Wrapper> getSelectedChanges() {
    List<Change> changes = myBrowser.getSelectedChanges();
    if (changes.isEmpty()) changes = myBrowser.getAllChanges();
    return ContainerUtil.map(changes, MyChangeWrapper::new);
  }

  @NotNull
  @Override
  protected List<Wrapper> getAllChanges() {
    return ContainerUtil.map(myBrowser.getAllChanges(), MyChangeWrapper::new);
  }

  @Override
  protected void selectChange(@NotNull Wrapper change) {
    ChangesTree tree = myBrowser.getViewer();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
    DefaultMutableTreeNode objectNode = TreeUtil.findNodeWithObject(root, change.getUserObject());
    TreePath path = objectNode != null ? TreeUtil.getPathFromRoot(objectNode) : null;
    if (path != null) {
      TreeUtil.selectPath(tree, path, false);
    }
  }

  public void updatePreview(boolean state) {
    // We do not have local changes here, so it's OK to always use `fromModelRefresh == false`
    if (state) {
      refresh(false);
    }
    else {
      clear();
    }
  }

  private class MyChangeWrapper extends Wrapper {
    @NotNull private final Change myChange;

    public MyChangeWrapper(@NotNull Change change) {
      myChange = change;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return myChange;
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      return myBrowser.getDiffRequestProducer(myChange);
    }
  }
}
