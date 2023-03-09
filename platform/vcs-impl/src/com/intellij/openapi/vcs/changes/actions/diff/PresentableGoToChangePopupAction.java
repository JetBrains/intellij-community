// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.Comparator.comparing;

public abstract class PresentableGoToChangePopupAction<T> extends GoToChangePopupBuilder.BaseGoToChangePopupAction {
  public static abstract class Default<T extends PresentableChange> extends PresentableGoToChangePopupAction<T> {
    @Override
    protected PresentableChange getPresentation(@NotNull T change) {
      return change;
    }
  }

  @NotNull
  protected abstract ListSelection<? extends T> getChanges();

  @Nullable
  protected abstract PresentableChange getPresentation(@NotNull T change);

  @Override
  protected boolean canNavigate() {
    return getChanges().getList().size() > 1;
  }

  @NotNull
  private DefaultTreeModel buildTreeModel(@NotNull Project project,
                                          @NotNull ChangesGroupingPolicyFactory grouping,
                                          @NotNull List<? extends T> changes) {
    MultiMap<ChangesBrowserNode.Tag, GenericChangesBrowserNode> groups = MultiMap.createLinked();

    for (int i = 0; i < changes.size(); i++) {
      PresentableChange change = getPresentation(changes.get(i));
      if (change == null) continue;

      FilePath filePath = change.getFilePath();
      FileStatus fileStatus = change.getFileStatus();
      ChangesBrowserNode.Tag tag = change.getTag();
      groups.putValue(tag, new GenericChangesBrowserNode(filePath, fileStatus, i));
    }

    MyTreeModelBuilder builder = new MyTreeModelBuilder(project, grouping);
    for (ChangesBrowserNode.Tag tag : groups.keySet()) {
      builder.setGenericNodes(groups.get(tag), tag);
    }
    return builder.build();
  }

  protected abstract void onSelected(@NotNull T change);

  @NotNull
  @Override
  protected JBPopup createPopup(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();

    Ref<JBPopup> popup = new Ref<>();
    MyChangesBrowser cb = new MyChangesBrowser(project, popup);

    popup.set(JBPopupFactory.getInstance()
                .createComponentPopupBuilder(cb, cb.getPreferredFocusedComponent())
                .setResizable(true)
                .setModalContext(false)
                .setFocusable(true)
                .setRequestFocus(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelOnOtherWindowOpen(true)
                .setMovable(true)
                .setCancelKeyEnabled(true)
                .setCancelOnClickOutside(true)
                .setDimensionServiceKey(project, "Diff.GoToChangePopup", false)
                .createPopup());

    return popup.get();
  }

  //
  // Helpers
  //

  private class MyChangesBrowser extends ChangesBrowserBase {
    @NotNull private final Ref<? extends JBPopup> myRef;
    @NotNull private final ListSelection<? extends T> myChanges;

    MyChangesBrowser(@NotNull Project project, @NotNull Ref<? extends JBPopup> popupRef) {
      super(project, false, false);
      myRef = popupRef;
      myChanges = PresentableGoToChangePopupAction.this.getChanges();
      myViewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      init();

      myViewer.rebuildTree();

      if (myChanges.getSelectedIndex() != -1) {
        UiNotifyConnector.doWhenFirstShown(this, () -> {
          DefaultMutableTreeNode toSelect = TreeUtil.findNode(myViewer.getRoot(), node -> {
            return node instanceof GenericChangesBrowserNode &&
                   ((GenericChangesBrowserNode)node).getIndex() == myChanges.getSelectedIndex();
          });
          if (toSelect != null) {
            TreeUtil.selectNode(myViewer, toSelect);
          }
        });
      }
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel() {
      return PresentableGoToChangePopupAction.this.buildTreeModel(myProject, getGrouping(), myChanges.getList());
    }

    @NotNull
    @Override
    protected List<AnAction> createToolbarActions() {
      return Collections.emptyList(); // remove diff action
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return Collections.emptyList(); // remove diff action
    }

    @Override
    protected void onDoubleClick() {
      myRef.get().cancel();

      ChangesBrowserNode<?> selection = VcsTreeModelData.selected(myViewer).iterateNodes().first();
      GenericChangesBrowserNode node = ObjectUtils.tryCast(selection, GenericChangesBrowserNode.class);
      if (node == null) return;

      T newSelection = myChanges.getList().get(node.getIndex());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> onSelected(newSelection));
    }
  }

  private static class GenericChangesBrowserNode extends ChangesBrowserNode<FilePath> implements Comparable<GenericChangesBrowserNode> {
    @NotNull private final FilePath myFilePath;
    @NotNull private final FileStatus myFileStatus;
    private final int myIndex;

    GenericChangesBrowserNode(@NotNull FilePath filePath, @NotNull FileStatus fileStatus, int index) {
      super(filePath);
      myFilePath = filePath;
      myFileStatus = fileStatus;
      myIndex = index;
    }

    @NotNull
    public FilePath getFilePath() {
      return myFilePath;
    }

    @NotNull
    public FileStatus getFileStatus() {
      return myFileStatus;
    }

    public int getIndex() {
      return myIndex;
    }

    @Override
    protected boolean isFile() {
      return !isDirectory();
    }

    @Override
    protected boolean isDirectory() {
      return myFilePath.isDirectory();
    }

    @Override
    public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
      renderer.appendFileName(myFilePath.getVirtualFile(), myFilePath.getName(), myFileStatus.getColor());

      if (renderer.isShowFlatten()) {
        appendParentPath(renderer, myFilePath.getParentPath());
      }

      if (!renderer.isShowFlatten() && getFileCount() != 1 || getDirectoryCount() != 0) {
        appendCount(renderer);
      }

      renderer.setIcon(myFilePath, myFilePath.isDirectory() || !isLeaf());
    }

    @Override
    public String getTextPresentation() {
      return myFilePath.getName();
    }

    @Override
    public String toString() {
      return FileUtil.toSystemDependentName(myFilePath.getPath());
    }

    @Override
    public int compareTo(@NotNull GenericChangesBrowserNode o) {
      return compareFilePaths(myFilePath, o.myFilePath);
    }
  }

  private static class MyTreeModelBuilder extends TreeModelBuilder {
    MyTreeModelBuilder(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping) {
      super(project, grouping);
    }

    public void setGenericNodes(@NotNull Collection<? extends GenericChangesBrowserNode> nodes, @Nullable ChangesBrowserNode.Tag tag) {
      ChangesBrowserNode<?> parentNode = createTagNode(tag);

      for (GenericChangesBrowserNode node : sorted(nodes, comparing(data -> data.getFilePath(), PATH_COMPARATOR))) {
        insertChangeNode(node.getFilePath(), parentNode, node);
      }
    }
  }
}
