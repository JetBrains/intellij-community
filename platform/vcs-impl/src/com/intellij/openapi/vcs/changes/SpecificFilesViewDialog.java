// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.CommonBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.actions.VirtualFileDeleteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;

abstract class SpecificFilesViewDialog extends DialogWrapper {
  protected final JPanel myPanel;
  protected final AsyncChangesTree myView;
  protected final Project myProject;

  protected SpecificFilesViewDialog(@NotNull Project project,
                                    @NotNull @NlsContexts.DialogTitle String title,
                                    @NotNull DataKey<Iterable<FilePath>> shownDataKey) {
    super(project, true);
    setTitle(title);
    myProject = project;

    myView = new MyChangesTree(project, shownDataKey);
    myView.setTreeStateStrategy(ChangesTree.KEEP_NON_EMPTY);

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
        myView.rebuildTree();
      }
    };
    ChangeListManager.getInstance(myProject).addChangeListListener(changeListListener, myDisposable);

    myView.rebuildTree();
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

  @NotNull
  @RequiresBackgroundThread
  protected abstract List<FilePath> getFiles();

  private class MyChangesTree extends AsyncChangesTree {
    private final @NotNull DataKey<Iterable<FilePath>> myShownDataKey;

    MyChangesTree(@NotNull Project project, @NotNull DataKey<Iterable<FilePath>> shownDataKey) {
      super(project, false, true);
      myShownDataKey = shownDataKey;
    }

    @Override
    public int getToggleClickCount() {
      return 2;
    }

    @Override
    public void installPopupHandler(@NotNull ActionGroup group) {
      PopupHandler.installPopupMenu(this, group, ActionPlaces.CHANGES_VIEW_POPUP);
    }

    @NotNull
    @Override
    protected AsyncChangesTreeModel getChangesTreeModel() {
      return SimpleAsyncChangesTreeModel.create(grouping -> {
        List<FilePath> files = SpecificFilesViewDialog.this.getFiles();
        return TreeModelBuilder.buildFromFilePaths(myProject, grouping, files);
      });
    }

    @Override
    public void resetTreeState() {
      ChangesBrowserNode<?> root = getRoot();
      if (root.getChildCount() == 1) {
        TreeNode child = root.getChildAt(0);
        expandPath(TreeUtil.getPathFromRoot(child));
      }
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
        VcsTreeModelData treeSelection = VcsTreeModelData.selected(this);
        VcsTreeModelData exactSelection = VcsTreeModelData.exactlySelected(this);
        DataProvider ourDataProvider = slowId -> getSlowData(myProject, treeSelection, exactSelection, slowId);
        DataProvider treeDataProvider = (DataProvider)VcsTreeModelData.getData(myProject, this, dataId);
        DataProvider superDataProvider = (DataProvider)super.getData(dataId);
        return CompositeDataProvider.compose(Arrays.asList(ourDataProvider, treeDataProvider, superDataProvider));
      }

      Object ourData = getFastData(dataId);
      if (ourData != null) return ourData;

      Object treeData = VcsTreeModelData.getData(myProject, this, dataId);
      if (treeData != null) return null;

      return super.getData(dataId);
    }

    private @Nullable Object getFastData(@NotNull String dataId) {
      if (myShownDataKey.is(dataId)) {
        return VcsTreeModelData.selected(this)
          .iterateUserObjects(FilePath.class);
      }
      if (VcsDataKeys.FILE_PATHS.is(dataId)) {
        return VcsTreeModelData.selected(this)
          .iterateUserObjects(FilePath.class);
      }
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        return new VirtualFileDeleteProvider();
      }
      if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
        return ChangesListView.HELP_ID;
      }
      return super.getData(dataId);
    }

    @Nullable
    private static Object getSlowData(@NotNull Project project,
                                      @NotNull VcsTreeModelData treeSelection,
                                      @NotNull VcsTreeModelData exactSelection,
                                      @NotNull String slowId) {
      if (ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY.is(slowId)) {
        return VcsTreeModelData.mapToExactVirtualFile(exactSelection);
      }
      return null;
    }
  }
}
