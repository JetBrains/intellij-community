// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;
import static com.intellij.util.containers.ContainerUtil.set;

abstract class SpecificFilesViewDialog extends DialogWrapper {
  protected JPanel myPanel;
  protected final ChangesListView myView;
  protected final ChangeListManager myChangeListManager;
  protected final Project myProject;

  protected SpecificFilesViewDialog(@NotNull Project project,
                                    @NotNull String title,
                                    @NotNull DataKey<Stream<VirtualFile>> shownDataKey,
                                    @NotNull List<VirtualFile> initDataFiles) {
    super(project, true);
    setTitle(title);
    myProject = project;
    final Runnable closer = () -> this.close(0);
    myView = new ChangesListView(project) {
      @Override
      public void calcData(DataKey key, DataSink sink) {
        super.calcData(key, sink);
        if (shownDataKey.is(key.getName())) {
          sink.put(shownDataKey, getSelectedVirtualFiles(null));
        }
      }

      @Override
      protected void editSourceRegistration() {
        EditSourceOnDoubleClickHandler.install(this, closer);
        EditSourceOnEnterKeyHandler.install(this, closer);
      }
    };
    myChangeListManager = ChangeListManager.getInstance(project);
    createPanel();
    setOKButtonText("Close");

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


  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private void initData(@NotNull final List<VirtualFile> files) {
    final TreeState state = TreeState.createOn(myView, (ChangesBrowserNode)myView.getModel().getRoot());

    DefaultTreeModel model = TreeModelBuilder.buildFromVirtualFiles(myProject, myView.getGrouping(), files);
    myView.setModel(model);
    myView.expandPath(new TreePath(((ChangesBrowserNode)model.getRoot()).getPath()));

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
    myView.getGroupingSupport().setGroupingKeysOrSkip(set(DEFAULT_GROUPING_KEYS));
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
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return !myView.getGroupingSupport().isNone();
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 1);
      TreeUtil.expand(myView, 0);
    }

    public boolean canCollapse() {
      return !myView.getGroupingSupport().isNone();
    }
  }

  protected void refreshView() {
    GuiUtils.invokeLaterIfNeeded(() -> {
      if (isVisible()) {
        initData(getFiles());
      }
    }, ModalityState.stateForComponent(myView));
  }

  @NotNull
  protected abstract List<VirtualFile> getFiles();
}
