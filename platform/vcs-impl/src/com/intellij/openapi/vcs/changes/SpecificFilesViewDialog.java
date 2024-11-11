// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.Collection;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;

abstract class SpecificFilesViewDialog<F> extends DialogWrapper {
  protected final JPanel myPanel;
  protected final AsyncChangesTree myView;
  protected final Project myProject;

  abstract static class SpecificFilePathsViewDialog extends SpecificFilesViewDialog<FilePath> {
    protected SpecificFilePathsViewDialog(@NotNull Project project,
                                          @NotNull @NlsContexts.DialogTitle String title,
                                          @NotNull DataKey<Iterable<FilePath>> shownDataKey,
                                          @NotNull SpecificFilesViewDialog.FilesSupplier<FilePath> filesSupplier) {
      super(project, title, new MyFilePathsTree(project, shownDataKey, filesSupplier));
    }

    private static final class MyFilePathsTree extends MyChangesTree {
      private final @NotNull DataKey<Iterable<FilePath>> myShownDataKey;
      private final @NotNull SpecificFilesViewDialog.FilesSupplier<FilePath> myFilesSupplier;

      MyFilePathsTree(@NotNull Project project,
                      @NotNull DataKey<Iterable<FilePath>> key, @NotNull SpecificFilesViewDialog.FilesSupplier<FilePath> filesSupplier) {
        super(project);
        myShownDataKey = key;
        myFilesSupplier = filesSupplier;
      }

      @Override
      protected @NotNull AsyncChangesTreeModel getChangesTreeModel() {
        return SimpleAsyncChangesTreeModel.create(grouping -> {
          Collection<FilePath> files = myFilesSupplier.getFiles();
          return TreeModelBuilder.buildFromFilePaths(myProject, grouping, files);
        });
      }

      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);

        VcsTreeModelData treeSelection = VcsTreeModelData.selected(this);
        sink.set(myShownDataKey, treeSelection.iterateUserObjects(FilePath.class));
        sink.set(VcsDataKeys.FILE_PATHS, treeSelection.iterateUserObjects(FilePath.class));
      }
    }
  }

  abstract static class SpecificVirtualFilesViewDialog extends SpecificFilesViewDialog<VirtualFile> {
    protected SpecificVirtualFilesViewDialog(@NotNull Project project,
                                             @NotNull @NlsContexts.DialogTitle String title,
                                             @NotNull DataKey<Iterable<VirtualFile>> shownDataKey,
                                             @NotNull SpecificFilesViewDialog.FilesSupplier<VirtualFile> filesSupplier) {
      super(project, title, new MyFilesTree(project, shownDataKey, filesSupplier));
    }

    private static final class MyFilesTree extends MyChangesTree {
      private final @NotNull DataKey<Iterable<VirtualFile>> myShownDataKey;
      private final @NotNull SpecificFilesViewDialog.FilesSupplier<VirtualFile> myFilesSupplier;

      MyFilesTree(@NotNull Project project,
                  @NotNull DataKey<Iterable<VirtualFile>> key, @NotNull SpecificFilesViewDialog.FilesSupplier<VirtualFile> filesSupplier) {
        super(project);
        myShownDataKey = key;
        myFilesSupplier = filesSupplier;
      }

      @Override
      protected @NotNull AsyncChangesTreeModel getChangesTreeModel() {
        return SimpleAsyncChangesTreeModel.create(grouping -> {
          Collection<VirtualFile> files = myFilesSupplier.getFiles();
          return TreeModelBuilder.buildFromVirtualFiles(myProject, grouping, files);
        });
      }

      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        VcsTreeModelData treeSelection = VcsTreeModelData.selected(this);
        sink.set(myShownDataKey, treeSelection.iterateUserObjects(VirtualFile.class));
        sink.set(VcsDataKeys.VIRTUAL_FILES, treeSelection.iterateUserObjects(VirtualFile.class));
      }
    }
  }

  protected SpecificFilesViewDialog(@NotNull Project project,
                                    @NotNull @NlsContexts.DialogTitle String title,
                                    @NotNull AsyncChangesTree tree) {
    super(project, true);
    setTitle(title);
    myProject = project;

    myView = tree;
    myView.setTreeStateStrategy(ChangesTree.KEEP_NON_EMPTY);

    final Runnable closer = () -> close(0);
    EditSourceOnEnterKeyHandler.install(myView, closer);
    EditSourceOnDoubleClickHandler.install(myView, closer);

    myView.setMinimumSize(new JBDimension(100, 100));
    myPanel = createPanel();
    setOKButtonText(CommonBundle.getCloseButtonText());

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

  protected interface FilesSupplier<F> {
    @NotNull
    @RequiresBackgroundThread
    Collection<F> getFiles();
  }

  private abstract static class MyChangesTree extends AsyncChangesTree {

    MyChangesTree(@NotNull Project project) {
      super(project, false, true);
    }

    @Override
    public int getToggleClickCount() {
      return 2;
    }

    @Override
    public void installPopupHandler(@NotNull ActionGroup group) {
      PopupHandler.installPopupMenu(this, group, ActionPlaces.CHANGES_VIEW_POPUP);
    }

    @Override
    public void resetTreeState() {
      ChangesBrowserNode<?> root = getRoot();
      if (root.getChildCount() == 1) {
        TreeNode child = root.getChildAt(0);
        expandPath(TreeUtil.getPathFromRoot(child));
      }
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      VcsTreeModelData.uiDataSnapshot(sink, myProject, this);

      VcsTreeModelData exactSelection = VcsTreeModelData.exactlySelected(this);
      sink.lazy(ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY, () ->
        VcsTreeModelData.mapToExactVirtualFile(exactSelection));
      sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new VirtualFileDeleteProvider());
      sink.set(PlatformCoreDataKeys.HELP_ID, ChangesListView.HELP_ID);
    }
  }
}
