// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsLogChangeProcessor extends ChangeViewDiffRequestProcessor {
  private final @NotNull VcsLogChangesBrowser myBrowser;

  private final boolean myIsInEditor;

  VcsLogChangeProcessor(@NotNull Project project, @NotNull VcsLogChangesBrowser browser, boolean isInEditor,
                        @NotNull Disposable disposable) {
    super(project, isInEditor ? DiffPlaces.DEFAULT : DiffPlaces.VCS_LOG_VIEW);
    myIsInEditor = isInEditor;
    myBrowser = browser;
    Disposer.register(disposable, this);

    myBrowser.addListener(() -> updatePreviewLater(), this);
    myBrowser.getViewer().addSelectionListener(this::updatePreviewLater, this);
  }

  @Override
  protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
    return !myIsInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents);
  }

  public @NotNull com.intellij.ui.components.panels.Wrapper getToolbarWrapper() {
    return myToolbarWrapper;
  }

  @Override
  public @NotNull Iterable<Wrapper> iterateSelectedChanges() {
    return wrap(VcsTreeModelData.selected(myBrowser.getViewer()));
  }

  @Override
  public @NotNull Iterable<Wrapper> iterateAllChanges() {
    return wrap(VcsTreeModelData.all(myBrowser.getViewer()));
  }

  private @NotNull Iterable<Wrapper> wrap(@NotNull VcsTreeModelData modelData) {
    return wrap(myBrowser, modelData);
  }

  static @NotNull Iterable<Wrapper> wrap(@NotNull VcsLogChangesBrowser browser, @NotNull VcsTreeModelData modelData) {
    return modelData.iterateNodes()
      .filter(ChangesBrowserChangeNode.class)
      .map(n -> new MyChangeWrapper(browser, n.getUserObject(), browser.getTag(n.getUserObject())));
  }

  @Override
  protected void selectChange(@NotNull Wrapper change) {
    myBrowser.selectChange(change.getUserObject(), change.getTag());
  }

  private void updatePreviewLater() {
    ApplicationManager.getApplication().invokeLater(() -> updatePreview(myIsInEditor || getComponent().isShowing()));
  }

  public void updatePreview(boolean state) {
    // We do not have local changes here, so it's OK to always use `fromModelRefresh == false`
    updatePreview(state, false);
  }

  public static @NotNull VcsTreeModelData getSelectedOrAll(VcsLogChangesBrowser changesBrowser) {
    boolean hasSelection = changesBrowser.getViewer().getSelectionModel().getSelectionCount() != 0;
    return hasSelection ? VcsTreeModelData.selected(changesBrowser.getViewer())
                        : VcsTreeModelData.all(changesBrowser.getViewer());
  }

  private static class MyChangeWrapper extends ChangeWrapper {
    private final @NotNull VcsLogChangesBrowser myBrowser;

    MyChangeWrapper(@NotNull VcsLogChangesBrowser browser, @NotNull Change change, @Nullable ChangesBrowserNode.Tag tag) {
      super(change, tag);
      myBrowser = browser;
    }

    @Override
    public @Nullable DiffRequestProducer createProducer(@Nullable Project project) {
      return myBrowser.getDiffRequestProducer(change, true);
    }
  }
}
