// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;

abstract class SpecificFilesViewDialog extends DialogWrapper {
  protected final JPanel myPanel;
  protected final ChangesListView myView;
  protected final Project myProject;

  private final BackgroundRefresher<@NotNull Runnable> myBackgroundRefresher;

  protected SpecificFilesViewDialog(@NotNull Project project,
                                    @NotNull @NlsContexts.DialogTitle String title,
                                    @NotNull DataKey<Iterable<FilePath>> shownDataKey) {
    super(project, true);
    setTitle(title);
    myProject = project;

    myBackgroundRefresher = new BackgroundRefresher<>(getClass().getSimpleName() + " refresh", getDisposable());
    myView = new ChangesListView(project, false) {

      @Nullable
      @Override
      public Object getData(@NotNull String dataId) {
        if (shownDataKey.is(dataId)) {
          return VcsTreeModelData.selected(this)
            .iterateUserObjects(FilePath.class);
        }
        return super.getData(dataId);
      }

      @Override
      public void onGroupingChanged() {
        refreshView();
      }
    };

    final Runnable closer = () -> close(0);
    EditSourceOnEnterKeyHandler.install(myView, closer);
    EditSourceOnDoubleClickHandler.install(myView, closer);

    myView.setMinimumSize(new JBDimension(100, 100));
    myPanel = createPanel();
    setOKButtonText(CommonBundle.getCancelButtonText());

    init();

    ChangeListAdapter changeListListener = new ChangeListAdapter() {
      @Override
      public void changeListUpdateDone() {
        refreshView();
      }
    };
    ChangeListManager.getInstance(myProject).addChangeListListener(changeListListener, myDisposable);

    refreshView();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }


  private JPanel createPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    final DefaultActionGroup group = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SPECIFIC_FILES_DIALOG", group, true);

    addCustomActions(group);

    final CommonActionsManager cam = CommonActionsManager.getInstance();
    final Expander expander = new Expander();
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP));

    DefaultActionGroup treeActions = new DefaultActionGroup();
    treeActions.add(cam.createExpandAllHeaderAction(expander, myView));
    treeActions.add(cam.createCollapseAllHeaderAction(expander, myView));

    JPanel toolbarPanel = new TreeActionsToolbarPanel(actionToolbar, treeActions, myView);

    panel.add(toolbarPanel, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myView), BorderLayout.CENTER);
    return panel;
  }

  protected void addCustomActions(@NotNull DefaultActionGroup group) {
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.vcs.changes.SpecificFilesViewDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myView;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private class Expander implements TreeExpander {
    @Override
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    @Override
    public boolean canExpand() {
      return !myView.getGroupingSupport().isNone();
    }

    @Override
    public void collapseAll() {
      TreeUtil.collapseAll(myView, 1);
      TreeUtil.expand(myView, 0);
    }

    @Override
    public boolean canCollapse() {
      return !myView.getGroupingSupport().isNone();
    }
  }

  private void updateTreeModel(@NotNull DefaultTreeModel treeModel) {
    final TreeState state = TreeState.createOn(myView);

    myView.setModel(treeModel);
    myView.expandPath(new TreePath(myView.getRoot().getPath()));

    state.applyTo(myView);
  }

  private @NotNull DefaultTreeModel buildTreeModel() {
    List<FilePath> files = getFiles();
    return TreeModelBuilder.buildFromFilePaths(myProject, myView.getGrouping(), files);
  }

  protected void refreshView() {
    myView.setPaintBusy(true);
    ModalityState modalityState = ModalityState.stateForComponent(myView);
    myBackgroundRefresher.requestRefresh(0, () -> {
        DefaultTreeModel treeModel = buildTreeModel();
        return () -> updateTreeModel(treeModel);
      })
      .thenAsync(callback -> AppUIExecutor.onUiThread(modalityState).submit(callback))
      .onProcessed(__ -> myView.setPaintBusy(false));
  }

  @NotNull
  protected abstract List<FilePath> getFiles();
}
