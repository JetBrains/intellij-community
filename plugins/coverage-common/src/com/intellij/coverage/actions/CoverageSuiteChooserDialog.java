// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions;

import com.intellij.CommonBundle;
import com.intellij.coverage.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;

public class CoverageSuiteChooserDialog extends DialogWrapper {
  @NonNls private static final String LOCAL = "Local";
  private final Project myProject;
  private final CheckboxTree mySuitesTree;
  private final CoverageDataManager myCoverageManager;
  private final CheckedTreeNode myRootNode;
  private CoverageEngine myEngine;

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
    group.add(new DeleteSuiteAction());
    group.add(new SwitchEngineAction());
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("CoverageSuiteChooser", group, true);
    toolbar.setTargetComponent(mySuitesTree);
    return toolbar.getComponent();
  }

  @Override
  protected void doOKAction() {
    final List<CoverageSuite> suites = collectSelectedSuites();
    final CoverageSuitesBundle bundle = suites.isEmpty() ? null : new CoverageSuitesBundle(suites.toArray(new CoverageSuite[0]));
    CoverageLogger.logSuiteImport(myProject, bundle);
    myCoverageManager.chooseSuitesBundle(bundle);
    ((CoverageDataManagerImpl)myCoverageManager).addRootsToWatch(suites);
    super.doOKAction();
  }

  @NotNull
  @Override
  protected List<ValidationInfo> doValidateAll() {
    CoverageEngine engine = null;
    for (CoverageSuite suite : collectSelectedSuites()) {
      if (engine == null) {
        engine = suite.getCoverageEngine();
        continue;
      }
      if (!Comparing.equal(engine, suite.getCoverageEngine())) {
        return Collections.singletonList(new ValidationInfo(CoverageBundle.message("cannot.show.coverage.reports.from.different.engines"), mySuitesTree));
      }
    }
    return super.doValidateAll();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), new NoCoverageAction(), getCancelAction()};
  }

  private Set<CoverageEngine> collectEngines() {
    final Set<CoverageEngine> engines = new HashSet<>();
    for (CoverageSuite suite : myCoverageManager.getSuites()) {
      engines.add(suite.getCoverageEngine());
    }
    return engines;
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
      if (treeNode instanceof CheckedTreeNode && ((CheckedTreeNode)treeNode).isChecked()) {
        final Object userObject = ((CheckedTreeNode)treeNode).getUserObject();
        if (userObject instanceof CoverageSuite) {
          suites.add((CoverageSuite)userObject);
        }
      }
      return true;
    });
    return suites;
  }

  private void initTree() {
    myRootNode.removeAllChildren();
    final HashMap<CoverageRunner, Map<String, List<CoverageSuite>>> grouped =
      new HashMap<>();
    groupSuites(grouped, myCoverageManager.getSuites(), myEngine);
    final CoverageSuitesBundle currentSuite = myCoverageManager.getCurrentSuitesBundle();
    final List<CoverageRunner> runners = new ArrayList<>(grouped.keySet());
    runners.sort((o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
    for (CoverageRunner runner : runners) {
      final DefaultMutableTreeNode runnerNode = new DefaultMutableTreeNode(getCoverageRunnerTitle(runner));
      final Map<String, List<CoverageSuite>> providers = grouped.get(runner);
      final DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode(CoverageBundle.message("remote.suites.node"));
      if (providers.size() == 1) {
        final String providersKey = providers.keySet().iterator().next();
        DefaultMutableTreeNode suitesNode = runnerNode;
        if (!Comparing.strEqual(providersKey, DefaultCoverageFileProvider.class.getName())) {
          suitesNode = remoteNode;
          runnerNode.add(remoteNode);
        }
        final List<CoverageSuite> suites = providers.get(providersKey);
        suites.sort((o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
        for (CoverageSuite suite : suites) {
          final CheckedTreeNode treeNode = new CheckedTreeNode(suite);
          treeNode.setChecked(currentSuite != null && currentSuite.contains(suite) ? Boolean.TRUE : Boolean.FALSE);
          suitesNode.add(treeNode);
        }
      }
      else {
        final DefaultMutableTreeNode localNode = new DefaultMutableTreeNode(LOCAL);
        runnerNode.add(localNode);
        runnerNode.add(remoteNode);
        for (String aClass : providers.keySet()) {
          DefaultMutableTreeNode node = Comparing.strEqual(aClass, DefaultCoverageFileProvider.class.getName()) ? localNode : remoteNode;
          for (CoverageSuite suite : providers.get(aClass)) {
            final CheckedTreeNode treeNode = new CheckedTreeNode(suite);
            treeNode.setChecked(currentSuite != null && currentSuite.contains(suite) ? Boolean.TRUE : Boolean.FALSE);
            node.add(treeNode);
          }
        }
      }
      myRootNode.add(runnerNode);
    }
  }

  private static void groupSuites(final HashMap<CoverageRunner, Map<String, List<CoverageSuite>>> grouped,
                                  final CoverageSuite[] suites,
                                  final CoverageEngine engine) {
    for (CoverageSuite suite : suites) {
      if (engine != null && suite.getCoverageEngine() != engine) continue;
      final CoverageFileProvider provider = suite.getCoverageDataFileProvider();
      if (provider instanceof DefaultCoverageFileProvider &&
          Comparing.strEqual(((DefaultCoverageFileProvider)provider).getSourceProvider(), DefaultCoverageFileProvider.class.getName())) {
        if (!provider.ensureFileExists()) continue;
      }
      final CoverageRunner runner = suite.getRunner();
      Map<String, List<CoverageSuite>> byProviders = grouped.get(runner);
      if (byProviders == null) {
        byProviders = new HashMap<>();
        grouped.put(runner, byProviders);
      }
      final String sourceProvider = provider instanceof DefaultCoverageFileProvider
                                    ? ((DefaultCoverageFileProvider)provider).getSourceProvider()
                                    : provider.getClass().getName();
      List<CoverageSuite> list = byProviders.get(sourceProvider);
      if (list == null) {
        list = new ArrayList<>();
        byProviders.put(sourceProvider, list);
      }
      list.add(suite);
    }
  }

  private void updateTree() {
    ((DefaultTreeModel)mySuitesTree.getModel()).reload();
    TreeUtil.expandAll(mySuitesTree);
  }

  private static class SuitesRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof CheckedTreeNode) {
        final Object userObject = ((CheckedTreeNode)value).getUserObject();
        if (userObject instanceof CoverageSuite suite) {
          getTextRenderer().append(suite.getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          final String date = " (" + DateFormatUtil.formatPrettyDateTime(suite.getLastCoverageTimeStamp()) + ")";
          getTextRenderer().append(date, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
      else if (value instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof String) {
          getTextRenderer().append((String)userObject);
        }
      }
    }
  }

  private class NoCoverageAction extends DialogWrapperAction {
    NoCoverageAction() {
      super(CoverageBundle.message("coverage.data.no.coverage.button"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myCoverageManager.chooseSuitesBundle(null);
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

          final CoverageSuite coverageSuite = myCoverageManager
            .addExternalCoverageSuite(file.getName(), file.getTimeStamp(), coverageRunner,
                                      new DefaultCoverageFileProvider(file.getPath()));

          final String coverageRunnerTitle = getCoverageRunnerTitle(coverageRunner);
          DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(myRootNode, coverageRunnerTitle);
          if (node == null) {
            node = new DefaultMutableTreeNode(coverageRunnerTitle);
            myRootNode.add(node);
          }
          if (node.getChildCount() > 0) {
            final TreeNode childNode = node.getChildAt(0);
            if (!(childNode instanceof CheckedTreeNode)) {
              if (LOCAL.equals(((DefaultMutableTreeNode)childNode).getUserObject())) {
                node = (DefaultMutableTreeNode)childNode;
              }
              else {
                final DefaultMutableTreeNode localNode = new DefaultMutableTreeNode(LOCAL);
                node.add(localNode);
                node = localNode;
              }
            }
          }
          final CheckedTreeNode suiteNode = new CheckedTreeNode(coverageSuite);
          suiteNode.setChecked(true);
          node.add(suiteNode);
          TreeUtil.sort(node, (o1, o2) -> {
            if (o1 instanceof CheckedTreeNode && o2 instanceof CheckedTreeNode) {
              final Object userObject1 = ((CheckedTreeNode)o1).getUserObject();
              final Object userObject2 = ((CheckedTreeNode)o2).getUserObject();
              if (userObject1 instanceof CoverageSuite && userObject2 instanceof CoverageSuite) {
                final String presentableName1 = ((CoverageSuite)userObject1).getPresentableName();
                final String presentableName2 = ((CoverageSuite)userObject2).getPresentableName();
                return presentableName1.compareToIgnoreCase(presentableName2);
              }
            }
            return 0;
          });
          updateTree();
          TreeUtil.selectNode(mySuitesTree, suiteNode);
        }
      }
    }
  }

  private class DeleteSuiteAction extends AnAction {
    DeleteSuiteAction() {
      super(CommonBundle.message("button.delete"), CommonBundle.message("button.delete"), PlatformIcons.DELETE_ICON);
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
          }
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private class SwitchEngineAction extends ComboBoxAction {
    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
      final DefaultActionGroup engChooser = new DefaultActionGroup();
      for (final CoverageEngine engine : collectEngines()) {
        engChooser.add(new AnAction(engine.getPresentableText()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myEngine = engine;
            initTree();
            updateTree();
          }
        });
      }
      return engChooser;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(collectEngines().size() > 1);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
