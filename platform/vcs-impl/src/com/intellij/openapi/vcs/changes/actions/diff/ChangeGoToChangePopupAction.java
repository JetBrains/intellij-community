// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated use {@link PresentableGoToChangePopupAction}
 */
@Deprecated(forRemoval = true)
public abstract class ChangeGoToChangePopupAction<Chain extends DiffRequestChain>
  extends GoToChangePopupBuilder.BaseGoToChangePopupAction {

  private final Chain myChain;

  public ChangeGoToChangePopupAction(@NotNull Chain chain) {
    myChain = chain;
  }

  @Override
  protected boolean canNavigate() {
    return myChain.getRequests().size() > 1;
  }

  @NotNull
  protected abstract DefaultTreeModel buildTreeModel(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping);

  protected abstract void onSelected(@Nullable ChangesBrowserNode object);

  @Nullable
  protected abstract Condition<? super DefaultMutableTreeNode> initialSelection();

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

    MyChangesBrowser(@NotNull Project project, @NotNull Ref<? extends JBPopup> popupRef) {
      super(project, false, false);
      myRef = popupRef;
      myViewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      init();

      myViewer.rebuildTree();

      Condition<? super DefaultMutableTreeNode> selectionCondition = initialSelection();
      if (selectionCondition != null) {
        UiNotifyConnector.doWhenFirstShown(this, () -> {
          DefaultMutableTreeNode node = TreeUtil.findNode(myViewer.getRoot(), selectionCondition);
          if (node != null) {
            TreeUtil.selectNode(myViewer, node);
          }
        });
      }
    }

    @NotNull
    @Override
    protected DefaultTreeModel buildTreeModel() {
      return ChangeGoToChangePopupAction.this.buildTreeModel(myProject, getGrouping());
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

      ChangesBrowserNode selection = VcsTreeModelData.selected(myViewer).nodesStream().findFirst().orElse(null);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> onSelected(selection));
    }
  }
}
