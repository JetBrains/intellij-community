// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Set;

class ShelvedPreviewProcessor extends TreeHandlerDiffRequestProcessor implements DiffPreviewUpdateProcessor {
  private final boolean myIsInEditor;

  private final @NotNull DiffShelvedChangesActionProvider.PatchesPreloader myPreloader;

  ShelvedPreviewProcessor(@NotNull Project project, @NotNull ShelvedChangesViewManager.ShelfTree tree, boolean isInEditor) {
    super(DiffPlaces.SHELVE_VIEW, tree, ShelveTreeDiffPreviewHandler.INSTANCE);
    myIsInEditor = isInEditor;
    myPreloader = new DiffShelvedChangesActionProvider.PatchesPreloader(project);

    putContextUserData(DiffShelvedChangesActionProvider.PatchesPreloader.SHELF_PRELOADER, myPreloader);

    new TreeHandlerChangesTreeTracker(tree, this, ShelveTreeDiffPreviewHandler.INSTANCE, !isInEditor).track();
  }

  @RequiresEdt
  @Override
  public void clear() {
    setCurrentChange(null);
    dropCaches();
  }

  @Override
  protected boolean shouldAddToolbarBottomBorder(@NotNull FrameDiffTool.ToolbarComponents toolbarComponents) {
    return !myIsInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents);
  }

  @Override
  protected @Nullable DiffRequest loadRequestFast(@NotNull DiffRequestProducer provider) {
    if (provider instanceof ShelvedWrapperDiffRequestProducer) {
      ShelvedChange shelvedChange = ((ShelvedWrapperDiffRequestProducer)provider).getWrapper().getShelvedChange();
      if (shelvedChange != null && myPreloader.isPatchFileChanged(shelvedChange.getPatchPath())) return null;
    }

    return super.loadRequestFast(provider);
  }

  private static class ShelveTreeDiffPreviewHandler extends ChangesTreeDiffPreviewHandler {
    public static final ShelveTreeDiffPreviewHandler INSTANCE = new ShelveTreeDiffPreviewHandler();

    @Override
    public @NotNull Iterable<? extends Wrapper> iterateSelectedChanges(@NotNull ChangesTree tree) {
      return VcsTreeModelData.selected(tree).iterateUserObjects(ShelvedWrapper.class);
    }

    @Override
    public @NotNull Iterable<? extends Wrapper> iterateAllChanges(@NotNull ChangesTree tree) {
      Set<ShelvedChangeList> changeLists =
        VcsTreeModelData.selected(tree).iterateUserObjects(ShelvedWrapper.class)
          .map(wrapper -> wrapper.getChangeList())
          .toSet();

      return VcsTreeModelData.all(tree).iterateRawNodes()
        .filter(node -> node instanceof ShelvedListNode && changeLists.contains(((ShelvedListNode)node).getList()))
        .flatMap(node -> VcsTreeModelData.allUnder(node).iterateUserObjects(ShelvedWrapper.class));
    }

    @Override
    public void selectChange(@NotNull ChangesTree tree, @NotNull ChangeViewDiffRequestProcessor.Wrapper change) {
      if (change instanceof ShelvedWrapper) {
        DefaultMutableTreeNode root = tree.getRoot();
        DefaultMutableTreeNode changelistNode = TreeUtil.findNodeWithObject(root, ((ShelvedWrapper)change).getChangeList());
        if (changelistNode == null) return;

        DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(changelistNode, change);
        if (node == null) return;
        TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false);
      }
    }
  }
}
