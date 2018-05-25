// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.util.Collections;
import java.util.List;

public abstract class ChangeGoToChangePopupAction<Chain extends DiffRequestChain>
  extends GoToChangePopupBuilder.BaseGoToChangePopupAction<Chain> {

  @Nullable private final Object myDefaultSelection;

  public ChangeGoToChangePopupAction(@NotNull Chain chain, @Nullable Object defaultSelection) {
    super(chain);
    myDefaultSelection = defaultSelection;
  }

  @NotNull
  protected abstract DefaultTreeModel buildTreeModel(@NotNull Project project, @NotNull ChangesGroupingPolicyFactory grouping);

  protected abstract void onSelected(@Nullable Object object);

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
    @NotNull private final Ref<JBPopup> myRef;

    public MyChangesBrowser(@NotNull Project project, @NotNull Ref<JBPopup> popupRef) {
      super(project, false, false);
      myRef = popupRef;
      myViewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      init();

      myViewer.rebuildTree();

      UiNotifyConnector.doWhenFirstShown(this, () -> {
        if (myDefaultSelection != null) selectEntries(Collections.singletonList(myDefaultSelection));
      });
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

      Object selection = ContainerUtil.getFirstItem(VcsTreeModelData.selected(myViewer).userObjects());
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        onSelected(selection);
      });
    }
  }
}
