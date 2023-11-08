// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions;

import com.intellij.CommonBundle;
import com.intellij.coverage.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CoverageSuiteChooserDialog extends DialogWrapper {
  @NonNls private static final String LOCAL = "Local";
  private final Project myProject;
  private final CheckboxTree mySuitesTree;
  private final CoverageDataManager myCoverageManager;
  private final CheckedTreeNode myRootNode;

  public CoverageSuiteChooserDialog(Project project) {
    super(project, true);
    myProject = project;
    myCoverageManager = CoverageDataManager.getInstance(project);

    myRootNode = new CheckedTreeNode("");
    initTree();
    mySuitesTree = new CheckboxTree(new SuitesRenderer(), myRootNode) {
      @Override
      protected void installSpeedSearch() {
        TreeSpeedSearch.installOn(this, false, path -> {
          final DefaultMutableTreeNode component = (DefaultMutableTreeNode)path.getLastPathComponent();
          final Object userObject = component.getUserObject();
          if (userObject instanceof CoverageSuite) {
            return ((CoverageSuite)userObject).getPresentableName();
          }
          return userObject.toString();
        });
      }
    };
    mySuitesTree.getEmptyText().appendText(CoverageBundle.message("no.coverage.suites.configured"));
    mySuitesTree.setRootVisible(false);
    mySuitesTree.setShowsRootHandles(false);
    TreeUtil.installActions(mySuitesTree);
    TreeUtil.expandAll(mySuitesTree);
    TreeUtil.promiseSelectFirst(mySuitesTree);
    mySuitesTree.setMinimumSize(new Dimension(25, -1));
    setOKButtonText(CoverageBundle.message("coverage.data.show.selected.button"));
    init();
    setTitle(CoverageBundle.message("choose.coverage.suite.to.display"));
  }

  @Override
  protected JComponent createCenterPanel() {
    return ScrollPaneFactory.createScrollPane(mySuitesTree);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySuitesTree;
  }

  @Override
  protected JComponent createNorthPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddExternalSuiteAction());
    group.add(new RemoveSuiteAction());
    group.add(new DeleteSuiteAction());
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CoverageSuiteChooser", group, true);
    toolbar.setTargetComponent(mySuitesTree);
    return toolbar.getComponent();
  }

  @Override
  protected void doOKAction() {
    final List<CoverageSuite> suites = collectSelectedSuites();
    Map<CoverageEngine, List<CoverageSuite>> byEngine = suites.stream().collect(Collectors.groupingBy((suite) -> suite.getCoverageEngine()));
    closeBundlesThatAreNotChosen(byEngine);

    for (List<CoverageSuite> suiteList : byEngine.values()) {
      CoverageSuitesBundle bundle = new CoverageSuitesBundle(suiteList.toArray(new CoverageSuite[0]));
      CoverageLogger.logSuiteImport(myProject, bundle);
      myCoverageManager.chooseSuitesBundle(bundle);
    }

    if (!suites.isEmpty()) {
      ExternalCoverageWatchManager.getInstance(myProject).addRootsToWatch(suites);
    }
    super.doOKAction();
  }

  private void closeBundlesThatAreNotChosen(Map<CoverageEngine, List<CoverageSuite>> byEngine) {
    Collection<CoverageSuitesBundle> activeSuites = myCoverageManager.activeSuites();
    Set<CoverageEngine> activeEngines = activeSuites.stream().map(b -> b.getCoverageEngine()).collect(Collectors.toSet());
    activeEngines.removeAll(byEngine.keySet());

    for (CoverageSuitesBundle bundle : activeSuites) {
      if (activeEngines.contains(bundle.getCoverageEngine())) {
        myCoverageManager.closeSuitesBundle(bundle);
      }
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), new NoCoverageAction(), getCancelAction()};
  }

  private static String getCoverageRunnerTitle(CoverageRunner coverageRunner) {
    return CoverageBundle.message("coverage.data.runner.name", coverageRunner.getPresentableName());
  }

  @Nullable
  private static CoverageRunner getCoverageRunner(@NotNull VirtualFile file) {
    for (CoverageRunner runner : CoverageRunner.EP_NAME.getExtensionList()) {
      for (String extension : runner.getDataFileExtensions()) {
        if (Comparing.strEqual(file.getExtension(), extension) && runner.canBeLoaded(VfsUtilCore.virtualToIoFile(file))) return runner;
      }
    }
    return null;
  }

  private List<CoverageSuite> collectSelectedSuites() {
    final List<CoverageSuite> suites = new ArrayList<>();
    TreeUtil.treeNodeTraverser(myRootNode).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(treeNode -> {
      if (treeNode instanceof CheckedTreeNode checkedTreeNode && checkedTreeNode.isChecked()) {
        final Object userObject = checkedTreeNode.getUserObject();
        if (userObject instanceof CoverageSuite suite) {
          suites.add(suite);
        }
      }
      return true;
    });
    return suites;
  }

  private void selectSuites(List<CoverageSuite> suites) {
    TreeUtil.treeNodeTraverser(myRootNode).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(treeNode -> {
      if (treeNode instanceof CheckedTreeNode checkedTreeNode) {
        final Object userObject = checkedTreeNode.getUserObject();
        checkedTreeNode.setChecked(userObject instanceof CoverageSuite && suites.contains(userObject));
      }
      return true;
    });
  }

  private void initTree() {
    myRootNode.removeAllChildren();
    final HashMap<CoverageRunner, Map<String, List<CoverageSuite>>> grouped = new HashMap<>();
    groupSuites(grouped, myCoverageManager.getSuites());
    final List<CoverageRunner> runners = new ArrayList<>(grouped.keySet());
    runners.sort((o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
    for (CoverageRunner runner : runners) {
      final DefaultMutableTreeNode runnerNode = new DefaultMutableTreeNode(getCoverageRunnerTitle(runner));
      myRootNode.add(runnerNode);
      final Map<String, List<CoverageSuite>> providers = grouped.get(runner);
      if (providers.size() == 1) {
        String providerKey = providers.keySet().iterator().next();
        DefaultMutableTreeNode parent = runnerNode;
        if (!isLocalProvider(providerKey)) {
          DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode(CoverageBundle.message("remote.suites.node"));
          parent.add(remoteNode);
          parent = remoteNode;
        }
        List<CoverageSuite> suites = providers.get(providerKey);
        createSuitesNodes(suites, parent);
      }
      else {
        DefaultMutableTreeNode localNode = new DefaultMutableTreeNode(LOCAL);
        DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode(CoverageBundle.message("remote.suites.node"));
        runnerNode.add(localNode);
        runnerNode.add(remoteNode);
        for (var entry : providers.entrySet()) {
          DefaultMutableTreeNode parent = isLocalProvider(entry.getKey()) ? localNode : remoteNode;
          createSuitesNodes(entry.getValue(), parent);
        }
      }
    }
  }

  private void createSuitesNodes(List<CoverageSuite> suites, DefaultMutableTreeNode parent) {
    suites.sort((o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
    for (CoverageSuite suite : suites) {
      CheckedTreeNode treeNode = new CheckedTreeNode(suite);
      treeNode.setChecked(isSuiteActive(suite));
      parent.add(treeNode);
    }
  }

  private static boolean isLocalProvider(String providerKey) {
    return Comparing.strEqual(providerKey, DefaultCoverageFileProvider.DEFAULT_LOCAL_PROVIDER_KEY);
  }

  private boolean isSuiteActive(CoverageSuite suite) {
    return ContainerUtil.exists(myCoverageManager.activeSuites(), bundle -> bundle.contains(suite));
  }

  private static void groupSuites(HashMap<CoverageRunner, Map<String, List<CoverageSuite>>> grouped,
                                  CoverageSuite[] suites) {
    for (CoverageSuite suite : suites) {
      CoverageFileProvider provider = suite.getCoverageDataFileProvider();
      if (provider instanceof DefaultCoverageFileProvider defaultProvider &&
          isLocalProvider(defaultProvider.getSourceProvider())
          && !provider.ensureFileExists()) {
        continue;
      }
      Map<String, List<CoverageSuite>> byProviders = grouped.computeIfAbsent(suite.getRunner(), (unused) -> new HashMap<>());
      String sourceProvider = provider instanceof DefaultCoverageFileProvider defaultProvider
                              ? defaultProvider.getSourceProvider()
                              : provider.getClass().getName();
      List<CoverageSuite> list = byProviders.computeIfAbsent(sourceProvider, (unused) -> new ArrayList<>());
      list.add(suite);
    }
  }

  private static class SuitesRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof CheckedTreeNode checkedTreeNode) {
        final Object userObject = checkedTreeNode.getUserObject();
        if (userObject instanceof CoverageSuite suite) {
          getTextRenderer().append(suite.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          final String date = " (" + DateFormatUtil.formatPrettyDateTime(suite.getLastCoverageTimeStamp()) + ")";
          getTextRenderer().append(date, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
      else if (value instanceof DefaultMutableTreeNode defaultNode) {
        final Object userObject = defaultNode.getUserObject();
        if (userObject instanceof @Nls String name) {
          getTextRenderer().append(name);
        }
      }
    }
  }

  class NoCoverageAction extends DialogWrapperAction {
    NoCoverageAction() {
      super(CoverageBundle.message("coverage.data.no.coverage.button"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      for (CoverageSuitesBundle suitesBundle : myCoverageManager.activeSuites()) {
        myCoverageManager.closeSuitesBundle(suitesBundle);
      }
      CoverageSuiteChooserDialog.this.close(DialogWrapper.OK_EXIT_CODE);
    }
  }

  private class AddExternalSuiteAction extends AnAction {
    AddExternalSuiteAction() {
      super(CommonBundle.message("button.add"), CommonBundle.message("button.add"), IconUtil.getAddIcon());
      registerCustomShortcutSet(CommonShortcuts.INSERT, mySuitesTree);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final VirtualFile[] files =
        FileChooser.chooseFiles(new FileChooserDescriptor(true, false, false, false, false, true) {
          @Override
          public boolean isFileSelectable(@Nullable VirtualFile file) {
            return file != null && getCoverageRunner(file) != null;
          }
        }, myProject, null);
      if (files.length > 0) {
        //ensure timestamp in vfs is updated
        VfsUtil.markDirtyAndRefresh(false, false, false, files);

        for (VirtualFile file : files) {
          final CoverageRunner coverageRunner = getCoverageRunner(file);
          if (coverageRunner == null) {
            Messages.showErrorDialog(myProject, CoverageBundle.message("no.coverage.runner.available.for", file.getName()), CommonBundle.getErrorTitle());
            continue;
          }

          CoverageSuite coverageSuite = myCoverageManager.addExternalCoverageSuite(file.getName(), file.getTimeStamp(), coverageRunner,
                                                                                   new DefaultCoverageFileProvider(file.getPath()));

          List<CoverageSuite> currentlySelected = collectSelectedSuites();
          currentlySelected.add(coverageSuite);
          initTree();
          selectSuites(currentlySelected);
          ((DefaultTreeModel)mySuitesTree.getModel()).reload();
          TreeUtil.expandAll(mySuitesTree);
        }
      }
    }
  }

  private class RemoveSuiteAction extends AnAction {
    RemoveSuiteAction() {
      super(CommonBundle.message("button.remove"), CommonBundle.message("button.remove"), PlatformIcons.DELETE_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final CheckedTreeNode[] selectedNodes = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      for (CheckedTreeNode selectedNode : selectedNodes) {
        final Object userObject = selectedNode.getUserObject();
        if (userObject instanceof CoverageSuite selectedSuite) {
          myCoverageManager.unregisterCoverageSuite(selectedSuite);
          TreeUtil.removeLastPathComponent(mySuitesTree, new TreePath(selectedNode.getPath()));
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final CheckedTreeNode[] selectedSuites = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(selectedSuites.length > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private class DeleteSuiteAction extends AnAction {
    DeleteSuiteAction() {
      super(CommonBundle.message("button.delete"), CommonBundle.message("button.delete"), AllIcons.Actions.GC);
      registerCustomShortcutSet(CommonShortcuts.getDelete(), mySuitesTree);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final CheckedTreeNode[] selectedNodes = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      for (CheckedTreeNode selectedNode : selectedNodes) {
        final Object userObject = selectedNode.getUserObject();
        if (userObject instanceof CoverageSuite selectedSuite) {
          if (selectedSuite.canRemove()) {
            myCoverageManager.removeCoverageSuite(selectedSuite);
            TreeUtil.removeLastPathComponent(mySuitesTree, new TreePath(selectedNode.getPath()));
          }
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final CheckedTreeNode[] selectedSuites = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      for (CheckedTreeNode node : selectedSuites) {
        final Object userObject = node.getUserObject();
        if (userObject instanceof CoverageSuite selectedSuite) {
          if (selectedSuite.canRemove()) {
            presentation.setEnabled(true);
            return;
          }
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
