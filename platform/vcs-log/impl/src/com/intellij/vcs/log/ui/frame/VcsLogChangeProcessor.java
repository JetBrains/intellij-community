// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.stream.Stream;

public class VcsLogChangeProcessor extends ChangeViewDiffRequestProcessor {
  @NotNull private final VcsLogChangesBrowser myBrowser;

  VcsLogChangeProcessor(@NotNull Project project, @NotNull VcsLogChangesBrowser browser, boolean isInEditor,
                        @NotNull Disposable disposable) {
    super(project, isInEditor ? DiffPlaces.DEFAULT : DiffPlaces.VCS_LOG_VIEW);
    myBrowser = browser;
    myContentPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    Disposer.register(disposable, this);

    myBrowser.addListener(() -> updatePreviewLater(), this);
    myBrowser.getViewer().addSelectionListener(this::updatePreviewLater, this);
  }

  @Override
  protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
    return false;
  }

  @NotNull
  public com.intellij.ui.components.panels.Wrapper getToolbarWrapper() {
    return myToolbarWrapper;
  }

  @NotNull
  @Override
  public Stream<Wrapper> getSelectedChanges() {
    boolean hasSelection = myBrowser.getViewer().getSelectionModel().getSelectionCount() != 0;
    return wrap(hasSelection ? VcsTreeModelData.selected(myBrowser.getViewer())
                             : VcsTreeModelData.all(myBrowser.getViewer()));
  }

  @NotNull
  @Override
  public Stream<Wrapper> getAllChanges() {
    return wrap(VcsTreeModelData.all(myBrowser.getViewer()));
  }


  @NotNull
  private Stream<Wrapper> wrap(@NotNull VcsTreeModelData modelData) {
    return modelData.userObjectsStream(Change.class).map(MyChangeWrapper::new);
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

  private void updatePreviewLater() {
    ApplicationManager.getApplication().invokeLater(() -> updatePreview(getComponent().isShowing()));
  }

  public void updatePreview(boolean state) {
    // We do not have local changes here, so it's OK to always use `fromModelRefresh == false`
    updatePreview(state, false);
  }

  private class MyChangeWrapper extends ChangeWrapper {
    MyChangeWrapper(@NotNull Change change) {
      super(change);
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      return myBrowser.getDiffRequestProducer(change, true);
    }
  }
}
