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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;

abstract class SpecificFilesViewDialog extends DialogWrapper {
  protected JPanel myPanel;
  protected final ChangesListView myView;
  protected final Project myProject;

  protected SpecificFilesViewDialog(@NotNull Project project,
                                    @NotNull @NlsContexts.DialogTitle String title,
                                    @NotNull DataKey<Iterable<FilePath>> shownDataKey,
                                    @NotNull List<? extends FilePath> initDataFiles) {
    super(project, true);
    setTitle(title);
    myProject = project;
    final Runnable closer = () -> this.close(0);
    myView = new ChangesListView(project, false) {
      @Nullable
      @Override
      public Object getData(@NotNull String dataId) {
        if (shownDataKey.is(dataId)) {
          return getSelectedFilePaths(null);
        }
        return super.getData(dataId);
      }
    };
    EditSourceOnEnterKeyHandler.install(myView, closer);
    EditSourceOnDoubleClickHandler.install(myView, closer);
    createPanel();
    setOKButtonText(CommonBundle.getCancelButtonText());

    init();
    initData(initDataFiles);
    myView.setMinimumSize(new JBDimension(100, 100));
    myView.addGroupingChangeListener(e -> refreshView());

    ChangeListAdapter changeListListener = new ChangeListAdapter() {
      @Override
      public void changeListUpdateDone() {
        refreshView();
      }
    };
    ChangeListManager.getInstance(myProject).addChangeListListener(changeListListener, myDisposable);
  }


  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }

  private void initData(@NotNull final List<? extends FilePath> files) {
    final TreeState state = TreeState.createOn(myView, (ChangesBrowserNode)myView.getModel().getRoot());

    DefaultTreeModel model = TreeModelBuilder.buildFromFilePaths(myProject, myView.getGrouping(), files);
    myView.setModel(model);
    myView.expandPath(new TreePath(((ChangesBrowserNode<?>)model.getRoot()).getPath()));

    state.applyTo(myView);
  }

  private void createPanel() {
    myPanel = new JPanel(new BorderLayout());

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

    myPanel.add(toolbarPanel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myView), BorderLayout.CENTER);
    myView.getGroupingSupport().setGroupingKeysOrSkip(Set.copyOf(DEFAULT_GROUPING_KEYS));
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

  protected void refreshView() {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.stateForComponent(myView), () -> {
      if (isVisible()) {
        initData(getFiles());
      }
    });
  }

  @NotNull
  protected abstract List<FilePath> getFiles();
}
