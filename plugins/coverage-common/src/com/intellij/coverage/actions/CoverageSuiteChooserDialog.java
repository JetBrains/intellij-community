package com.intellij.coverage.actions;

import com.intellij.coverage.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
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
import java.util.*;
import java.util.List;

public class CoverageSuiteChooserDialog extends DialogWrapper {
  @NonNls private static final String LOCAL = "Local";
  private final Project myProject;
  private final CheckboxTree mySuitesTree;
  private final CoverageDataManager myCoverageManager;
  private static final Logger LOG = Logger.getInstance(CoverageSuiteChooserDialog.class);
  private final CheckedTreeNode myRootNode;
  private CoverageEngine myEngine;

  public CoverageSuiteChooserDialog(Project project) {
    super(project, true);
    myProject = project;
    myCoverageManager = CoverageDataManager.getInstance(project);

    myRootNode = new CheckedTreeNode("");
    initTree();
    mySuitesTree = new CheckboxTree(new SuitesRenderer(), myRootNode) {
      protected void installSpeedSearch() {
        new TreeSpeedSearch(this, path -> {
          final DefaultMutableTreeNode component = (DefaultMutableTreeNode)path.getLastPathComponent();
          final Object userObject = component.getUserObject();
          if (userObject instanceof CoverageSuite) {
            return ((CoverageSuite)userObject).getPresentableName();
          }
          return userObject.toString();
        });
      }
    };
    mySuitesTree.getEmptyText().appendText("No coverage suites configured.");
    mySuitesTree.setRootVisible(false);
    mySuitesTree.setShowsRootHandles(false);
    TreeUtil.installActions(mySuitesTree);
    TreeUtil.expandAll(mySuitesTree);
    TreeUtil.selectFirstNode(mySuitesTree);
    mySuitesTree.setMinimumSize(new Dimension(25, -1));
    setOKButtonText("Show selected");
    init();
    setTitle("Choose Coverage Suite to Display");
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
    return ActionManager.getInstance().createActionToolbar("CoverageSuiteChooser", group, true).getComponent();
  }

  @Override
  protected void doOKAction() {
    final List<CoverageSuite> suites = collectSelectedSuites();
    myCoverageManager.chooseSuitesBundle(suites.isEmpty() ? null : new CoverageSuitesBundle(suites.toArray(new CoverageSuite[suites.size()])));
    super.doOKAction();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
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
    return coverageRunner.getPresentableName() + " Coverage";
  }

  @Nullable
  private static CoverageRunner getCoverageRunner(VirtualFile file) {
    for (CoverageRunner runner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
      if (Comparing.strEqual(file.getExtension(), runner.getDataFileExtension())) return runner;
    }
    return null;
  }

  private List<CoverageSuite> collectSelectedSuites() {
    final List<CoverageSuite> suites = new ArrayList<>();
    TreeUtil.traverse(myRootNode, treeNode -> {
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
    Collections.sort(runners, (o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
    for (CoverageRunner runner : runners) {
      final DefaultMutableTreeNode runnerNode = new DefaultMutableTreeNode(getCoverageRunnerTitle(runner));
      final Map<String, List<CoverageSuite>> providers = grouped.get(runner);
      final DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode("Remote");
      if (providers.size() == 1) {
        final String providersKey = providers.keySet().iterator().next();
        DefaultMutableTreeNode suitesNode = runnerNode;
        if (!Comparing.strEqual(providersKey, DefaultCoverageFileProvider.class.getName())) {
          suitesNode = remoteNode;
          runnerNode.add(remoteNode);
        }
        final List<CoverageSuite> suites = providers.get(providersKey);
        Collections.sort(suites, (o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));
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
          DefaultMutableTreeNode node = Comparing.strEqual(aClass, DefaultCoverageFileProvider.class.getName())  ? localNode : remoteNode;
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
        if (userObject instanceof CoverageSuite) {
          CoverageSuite suite = (CoverageSuite)userObject;
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
    public NoCoverageAction() {
      super("&No Coverage");
    }

    @Override
    protected void doAction(ActionEvent e) {
      myCoverageManager.chooseSuitesBundle(null);
      CoverageSuiteChooserDialog.this.close(DialogWrapper.OK_EXIT_CODE);
    }
  }

  private class AddExternalSuiteAction extends AnAction {
    public AddExternalSuiteAction() {
      super("Add", "Add", IconUtil.getAddIcon());
      registerCustomShortcutSet(CommonShortcuts.INSERT, mySuitesTree);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final VirtualFile file =
        FileChooser.chooseFile(new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return getCoverageRunner(file) != null;
          }
        }, myProject, null);
      if (file != null) {
        final CoverageRunner coverageRunner = getCoverageRunner(file);
        LOG.assertTrue(coverageRunner != null, file.getExtension());

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
            } else {
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

  private class DeleteSuiteAction extends AnAction {
    public DeleteSuiteAction() {
      super("Delete", "Delete", PlatformIcons.DELETE_ICON);
      registerCustomShortcutSet(CommonShortcuts.getDelete(), mySuitesTree);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final CheckedTreeNode[] selectedNodes = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      for (CheckedTreeNode selectedNode : selectedNodes) {
        final Object userObject = selectedNode.getUserObject();
        if (userObject instanceof CoverageSuite) {
          final CoverageSuite selectedSuite = (CoverageSuite)userObject;
          if (selectedSuite.getCoverageDataFileProvider() instanceof DefaultCoverageFileProvider &&
              Comparing.strEqual(((DefaultCoverageFileProvider)selectedSuite.getCoverageDataFileProvider()).getSourceProvider(),
                                 DefaultCoverageFileProvider.class.getName())) {
            myCoverageManager.removeCoverageSuite(selectedSuite);
            TreeUtil.removeLastPathComponent(mySuitesTree, new TreePath(selectedNode.getPath()));
          }
        }
      }
    }

    @Override
    public void update(AnActionEvent e) {
      final CheckedTreeNode[] selectedSuites = mySuitesTree.getSelectedNodes(CheckedTreeNode.class, null);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      for (CheckedTreeNode node : selectedSuites) {
        final Object userObject = node.getUserObject();
        if (userObject instanceof CoverageSuite) {
          final CoverageSuite selectedSuite = (CoverageSuite)userObject;
          if (selectedSuite.getCoverageDataFileProvider() instanceof DefaultCoverageFileProvider &&
              Comparing.strEqual(((DefaultCoverageFileProvider)selectedSuite.getCoverageDataFileProvider()).getSourceProvider(),
                                 DefaultCoverageFileProvider.class.getName())) {
            presentation.setEnabled(true);
          }
        }
      }
    }
  }

  private class SwitchEngineAction extends ComboBoxAction {
    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup engChooser = new DefaultActionGroup();
      for (final CoverageEngine engine : collectEngines()) {
        engChooser.add(new AnAction(engine.getPresentableText()) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myEngine = engine;
            initTree();
            updateTree();
          }
        });
      }
      return engChooser;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(collectEngines().size() > 1);
    }
  }
}
