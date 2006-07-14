package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.CollapseAllToolbarAction;
import com.intellij.ide.actions.CommonActionsFactory;
import com.intellij.ide.actions.ExpandAllToolbarAction;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.AntBuildFilePropertiesAction;
import com.intellij.lang.ant.config.actions.RemoveBuildFileAction;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.config.impl.*;
import com.intellij.lang.ant.config.impl.configuration.BuildFilePropertiesPanel;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.KeymapManagerListener;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AntExplorer extends JPanel implements DataProvider {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.explorer.AntExplorer");

  private Project myProject;
  private AntExplorerTreeBuilder myBuilder;
  private Tree myTree;
  private KeymapListener myKeymapListener;
  private List<Disposable> myDisposables = new ArrayList<Disposable>();
  private final AntBuildFilePropertiesAction myAntBuildFilePropertiesAction;

  private final TreeExpander myTreeExpander = new TreeExpander() {
    public void expandAll() {
      myBuilder.expandAll();
    }

    public boolean canExpand() {
      return !AntConfiguration.getInstance(myProject).getBuildFiles().isEmpty();
    }

    public void collapseAll() {
      myBuilder.collapseAll();
    }

    public boolean canCollapse() {
      return !AntConfiguration.getInstance(myProject).getBuildFiles().isEmpty();
    }
  };

  public AntExplorer(final Project project) {
    super(new BorderLayout(0, 2));
    myProject = project;
    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new NodeRenderer());
    myBuilder = new AntExplorerTreeBuilder(project, myTree, model);
    myBuilder.setTargetsFiltered(AntConfiguration.getInstance(project).isFilterTargets());
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null) {
            runSelection(DataManager.getInstance().getDataContext(myTree));
          }
        }
      }
    });
    myTree.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        runSelection(DataManager.getInstance().getDataContext(myTree));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    myTree.expandRow(0);
    myTree.setLineStyleAngled();
    myAntBuildFilePropertiesAction = new AntBuildFilePropertiesAction(this);
    add(createToolbarPanel(), BorderLayout.NORTH);
    add(new JScrollPane(myTree), BorderLayout.CENTER);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myKeymapListener = new KeymapListener();
  }

  public void dispose() {
    for (final Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
    myProject = null;
    if (myKeymapListener != null) {
      myKeymapListener.stopListen();
      myKeymapListener = null;
    }
    else {
      LOG.error("already disposed");
    }
    if (myBuilder != null) {
      Disposer.dispose(myBuilder);
    }
    myBuilder = null;
    if (myTree != null) {
      ToolTipManager.sharedInstance().unregisterComponent(myTree);
      final KeyStroke[] strokes = myTree.getRegisteredKeyStrokes();
      for (KeyStroke keyStroke : strokes) {
        myTree.unregisterKeyboardAction(keyStroke);
      }
      myTree = null;
    }
  }

  private JPanel createToolbarPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddAction());
    group.add(new RemoveAction());
    group.add(new RunAction());
    group.add(new ShowAllTargetsAction());
    group.add(new ExpandAllToolbarAction(myTreeExpander, AntBundle.message("ant.explorer.expand.all.nodes.action.description")));
    group.add(new CollapseAllToolbarAction(myTreeExpander, AntBundle.message("ant.explorer.collapse.all.nodes.action.description")));
    group.add(myAntBuildFilePropertiesAction);
    group.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(HelpID.ANT));

    ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_EXPLORER_TOOLBAR, group, true);
    JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    return buttonsPanel;
  }

  private void addBuildFile() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createXmlDescriptor();
    descriptor.setTitle(AntBundle.message("select.ant.build.file.dialog.title"));
    descriptor.setDescription(AntBundle.message("select.ant.build.file.dialog.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
    if (files.length == 0) {
      return;
    }
    final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
    final ArrayList<VirtualFile> ignoredFiles = new ArrayList<VirtualFile>();
    for (VirtualFile file : files) {
      try {
        antConfiguration.addBuildFile(file);
      }
      catch (NoPsiFileException e) {
        ignoredFiles.add(e.getFile());
      }
    }
    if (ignoredFiles.size() != 0) {
      String messageText;
      final StringBuilder message = StringBuilderSpinAllocator.alloc();
      try {
        String separator = "";
        for (final VirtualFile virtualFile : ignoredFiles) {
          message.append(separator);
          message.append(virtualFile.getPresentableUrl());
          separator = "\n";
        }
        messageText = message.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(message);
      }
      Messages.showWarningDialog(myProject, messageText, AntBundle.message("cannot.add.ant.files.dialog.title"));
    }
  }

  public void removeBuildFile() {
    AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null) {
      return;
    }
    String fileName = buildFile.getPresentableUrl();
    int result = Messages.showYesNoDialog(myProject, AntBundle.message("remove.the.reference.to.file.confirmation.text", fileName),
                                          AntBundle.message("confirm.remove.dialog.title"), Messages.getQuestionIcon());
    if (result != 0) {
      return;
    }
    AntConfiguration.getInstance(myProject).removeBuildFile(buildFile);
  }

  public void setBuildFileProperties(DataContext dataContext) {
    final AntBuildFile buildFile = getCurrentBuildFile();
    if (BuildFilePropertiesPanel.editBuildFile(getCurrentBuildFile())) {
      AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
      antConfiguration.updateBuildFile(buildFile);
      myBuilder.refresh();
      myTree.repaint();
    }
  }

  private void runSelection(final DataContext dataContext) {
    if (!canRunSelection()) {
      return;
    }
    AntBuildFile buildFile = getCurrentBuildFile();
    TreePath[] paths = myTree.getSelectionPaths();
    String[] targets = getTargetNamesFromPaths(paths);
    ExecutionHandler.runBuild(buildFile, targets, null, dataContext, AntBuildListener.NULL);
  }

  private boolean canRunSelection() {
    TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return false;
    }
    AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null) {
      return false;
    }
    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      AntBuildFileNodeDescriptor buildFileNodeDescriptor;
      if (userObject instanceof AntTargetNodeDescriptor) {
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject();
      }
      else {
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)userObject;
      }
      if (buildFileNodeDescriptor.getBuildFile() != buildFile) {
        return false;
      }
    }
    return true;
  }

  private static String[] getTargetNamesFromPaths(TreePath[] paths) {
    final List<String> targets = new ArrayList<String>();
    for (TreePath path : paths) {
      Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(userObject instanceof AntTargetNodeDescriptor)) {
        continue;
      }
      AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getTarget();
      if (target instanceof MetaTarget) {
        targets.addAll(Arrays.asList(((MetaTarget)target).getTargetNames()));
      }
      else {
        targets.add(target.getName());
      }
    }
    return targets.toArray(new String[targets.size()]);
  }

  private static AntBuildTarget[] getTargetObjectsFromPaths(TreePath[] paths) {
    final List<AntBuildTarget> targets = new ArrayList<AntBuildTarget>();
    for (TreePath path : paths) {
      Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(userObject instanceof AntTargetNodeDescriptor)) {
        continue;
      }
      AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getTarget();
      targets.add(target);

    }
    return targets.toArray(new AntBuildTarget[targets.size()]);
  }

  public boolean isBuildFileSelected() {
    return myProject != null && getCurrentBuildFile() != null;
  }

  @Nullable
  private AntBuildFile getCurrentBuildFile() {
    AntBuildFileNodeDescriptor descriptor = getCurrentBuildFileNodeDescriptor();
    return (descriptor == null) ? null : descriptor.getBuildFile();
  }

  @Nullable
  private AntBuildFileNodeDescriptor getCurrentBuildFileNodeDescriptor() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    while (node != null) {
      Object userObject = node.getUserObject();
      if (userObject instanceof AntBuildFileNodeDescriptor) {
        return (AntBuildFileNodeDescriptor)userObject;
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private void popupInvoked(final Component comp, final int x, final int y) {
    Object userObject = null;
    TreePath path = myTree.getSelectionPath();
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null) {
        userObject = node.getUserObject();
      }
    }
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RunAction());
    group.add(new CreateMetaTargetAction());
    group.add(new RemoveMetaTargetsOrBuildFileAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    if (userObject instanceof AntBuildFileNodeDescriptor) {
      group.add(new RemoveBuildFileAction(this));
    }
    if (userObject instanceof AntTargetNodeDescriptor) {
      AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getTarget();
      DefaultActionGroup executeOnGroup = new DefaultActionGroup(AntBundle.message("ant.explorer.execute.on.action.group.name"), true);
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteBeforeCompilationEvent.getInstance()));
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteAfterCompilationEvent.getInstance()));
      executeOnGroup.addSeparator();
      executeOnGroup.add(new ExecuteBeforeRunAction(target, getCurrentBuildFile()));
      group.add(executeOnGroup);
      group.add(new AssignShortcutAction(target.getActionId()));
    }
    group.add(myAntBuildFilePropertiesAction);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.ANT_EXPLORER_POPUP, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (DataConstants.NAVIGATABLE.equals(dataId)) {
      AntBuildFile buildFile = getCurrentBuildFile();
      if (buildFile == null) {
        return null;
      }
      AntFile file = buildFile.getAntFile();
      if (file == null) {
        return null;
      }
      TreePath treePath = myTree.getLeadSelectionPath();
      if (treePath == null) {
        return null;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      if (node == null) {
        return null;
      }
      if (node.getUserObject()instanceof AntTargetNodeDescriptor) {
        AntTargetNodeDescriptor targetNodeDescriptor = (AntTargetNodeDescriptor)node.getUserObject();
        AntBuildTarget buildTarget = targetNodeDescriptor.getTarget();
        final OpenFileDescriptor descriptor = buildTarget.getOpenFileDescriptor();
        if (descriptor != null) {
          final VirtualFile descriptorFile = descriptor.getFile();
          if (descriptorFile != null && descriptorFile.isValid()) {
            return descriptor;
          }
        }
      }
      if (file.isValid()) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null && virtualFile.isValid()) {
          return new OpenFileDescriptor(myProject, virtualFile);
        }
      }
    }
    else if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HelpID.ANT;
    }
    else if (DataConstantsEx.TREE_EXPANDER.equals(dataId)) {
      return myTreeExpander;
    }
    else if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return null;
      }
      ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
      for (TreePath path : paths) {
        for (DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
             node != null;
             node = (DefaultMutableTreeNode)node.getParent()) {
          Object userObject = node.getUserObject();
          if (!(userObject instanceof AntBuildFileNodeDescriptor)) {
            continue;
          }
          AntBuildFile buildFile = ((AntBuildFileNodeDescriptor)userObject).getBuildFile();
          if (buildFile != null) {
            VirtualFile virtualFile = buildFile.getVirtualFile();
            if (virtualFile != null) {
              result.add(virtualFile);
            }
          }
          break;
        }
      }
      if (result.size() == 0) {
        return null;
      }
      return result.toArray(new VirtualFile[result.size()]);
    }
    return null;
  }

  private static final class NodeRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof AntNodeDescriptor) {
        AntNodeDescriptor descriptor = (AntNodeDescriptor)userObject;
        descriptor.customize(this);
      }
      else {
        append(tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private final class AddAction extends AnAction {
    public AddAction() {
      super(AntBundle.message("add.ant.file.action.name"), AntBundle.message("add.ant.file.action.description"),
            IconLoader.getIcon("/general/add.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      addBuildFile();
    }
  }

  private final class RemoveAction extends AnAction {
    public RemoveAction() {
      super(AntBundle.message("remove.ant.file.action.name"), AntBundle.message("remove.ant.file.action.description"),
            IconLoader.getIcon("/general/remove.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      removeBuildFile();
    }

    public void update(AnActionEvent event) {
      event.getPresentation().setEnabled(getCurrentBuildFile() != null);
    }
  }

  private final class RunAction extends AnAction {
    public RunAction() {
      super(AntBundle.message("run.ant.file.or.target.action.name"), AntBundle.message("run.ant.file.or.target.action.description"),
            IconLoader.getIcon("/actions/execute.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      runSelection(e.getDataContext());
    }

    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();

      String place = event.getPlace();
      if (ActionPlaces.ANT_EXPLORER_TOOLBAR.equals(place)) {
        presentation.setText(AntBundle.message("run.ant.file.or.target.action.name"));
      }
      else {
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null && paths.length == 1 &&
            ((DefaultMutableTreeNode)paths[0].getLastPathComponent()).getUserObject()instanceof AntBuildFileNodeDescriptor) {
          presentation.setText(AntBundle.message("run.ant.build.action.name"));
        }
        else {
          if (paths == null || paths.length == 1) {
            presentation.setText(AntBundle.message("run.ant.target.action.name"));
          }
          else {
            presentation.setText(AntBundle.message("run.ant.targets.action.name"));
          }
        }
      }

      presentation.setEnabled(canRunSelection());
    }
  }

  private final class ShowAllTargetsAction extends ToggleAction {
    public ShowAllTargetsAction() {
      super(AntBundle.message("filter.ant.targets.action.name"), AntBundle.message("filter.ant.targets.action.description"),
            IconLoader.getIcon("/ant/filter.png"));
    }

    public boolean isSelected(AnActionEvent event) {
      return AntConfiguration.getInstance(myProject).isFilterTargets();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setTargetsFiltered(flag);
    }
  }

  private void setTargetsFiltered(boolean value) {
    myBuilder.setTargetsFiltered(value);
    AntConfiguration.getInstance(myProject).setFilterTargets(value);
  }

  private final class ExecuteOnEventAction extends ToggleAction {
    private final AntBuildTarget myTarget;
    private final ExecutionEvent myExecutionEvent;

    public ExecuteOnEventAction(final AntBuildTarget target, final ExecutionEvent executionEvent) {
      super(executionEvent.getPresentableName());
      myTarget = target;
      myExecutionEvent = executionEvent;
    }

    public boolean isSelected(AnActionEvent e) {
      return myTarget.equals(AntConfiguration.getInstance(myProject).getTargetForEvent(myExecutionEvent));
    }

    public void setSelected(AnActionEvent event, boolean state) {
      final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
      if (state) {
        final AntBuildFile buildFile =
          (myTarget instanceof MetaTarget) ? ((MetaTarget)myTarget).getBuildFile() : myTarget.getModel().getBuildFile();
        antConfiguration.setTargetForEvent(buildFile, myTarget.getName(), myExecutionEvent);
      }
      else {
        antConfiguration.clearTargetForEvent(myExecutionEvent);
      }
      myBuilder.refresh();
    }
  }

  private final class ExecuteBeforeRunAction extends AnAction {
    private final AntBuildTarget myTarget;
    private final AntBuildFile myBuildFile;

    public ExecuteBeforeRunAction(final AntBuildTarget target, final AntBuildFile buildFile) {
      super(AntBundle.message("executes.before.run.debug.acton.name"));
      myTarget = target;
      myBuildFile = buildFile;
    }

    public void actionPerformed(AnActionEvent e) {
      ExecuteOnRunDialog dialog = new ExecuteOnRunDialog(myProject, myTarget, myBuildFile);
      dialog.show();
      myBuilder.refresh();
    }
  }

  private final class CreateMetaTargetAction extends AnAction {

    public CreateMetaTargetAction() {
      super(AntBundle.message("ant.create.meta.target.action.name"), AntBundle.message("ant.create.meta.target.action.description"), null
/*IconLoader.getIcon("/actions/execute.png")*/);
    }

    public void actionPerformed(AnActionEvent e) {
      final AntBuildFile buildFile = getCurrentBuildFile();
      final String[] targets = getTargetNamesFromPaths(myTree.getSelectionPaths());
      final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
      final ExecuteCompositeTargetEvent event = new ExecuteCompositeTargetEvent(targets);
      final SaveMetaTargetDialog dialog = new SaveMetaTargetDialog(myTree, event, antConfiguration, buildFile);
      dialog.setTitle(e.getPresentation().getText());
      dialog.show();
      if (dialog.isOK()) {
        myBuilder.refresh();
        myTree.repaint();
      }
    }

    public void update(AnActionEvent e) {
      TreePath[] paths = myTree.getSelectionPaths();
      e.getPresentation().setEnabled(paths != null && paths.length > 1 && canRunSelection());
    }
  }

  private final class RemoveMetaTargetsOrBuildFileAction extends AnAction {

    public RemoveMetaTargetsOrBuildFileAction() {
      super(AntBundle.message("remove.meta.targets.action.name"), AntBundle.message("remove.meta.targets.action.description"), null);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)), myTree);
      myDisposables.add(new Disposable() {
        public void dispose() {
          RemoveMetaTargetsOrBuildFileAction.this.unregisterCustomShortcutSet(myTree);
        }
      });
      myTree.registerKeyboardAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          doAction();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public void actionPerformed(AnActionEvent e) {
      doAction();
    }

    private void doAction() {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return;
      }
      try {
        // try to remove build file
        if (paths.length == 1) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
          if (node.getUserObject()instanceof AntBuildFileNodeDescriptor) {
            final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
            if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
              removeBuildFile();
              return;
            }
          }
        }
        // try to remove meta targets
        final AntBuildTarget[] targets = getTargetObjectsFromPaths(paths);
        final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
        for (final AntBuildTarget buildTarget : targets) {
          if (buildTarget instanceof MetaTarget) {
            for (final ExecutionEvent event : antConfiguration.getEventsForTarget(buildTarget)) {
              if (event instanceof ExecuteCompositeTargetEvent) {
                antConfiguration.clearTargetForEvent(event);
              }
            }
          }
        }
      }
      finally {
        myBuilder.refresh();
        myTree.repaint();
      }
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        presentation.setEnabled(false);
        return;
      }

      if (paths.length == 1) {
        String text = AntBundle.message("remove.meta.target.action.name");
        boolean enabled = false;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
        if (node.getUserObject()instanceof AntBuildFileNodeDescriptor) {
          final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
          if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
            text = AntBundle.message("remove.selected.build.file.action.name");
            enabled = true;
          }
        }
        presentation.setText(text);
        presentation.setEnabled(enabled);
      }
      else {
        presentation.setText(AntBundle.message("remove.selected.meta.targets.action.name"));
        final AntBuildTarget[] targets = getTargetObjectsFromPaths(paths);
        boolean enabled = targets.length > 0;
        for (final AntBuildTarget buildTarget : targets) {
          if (!(buildTarget instanceof MetaTarget)) {
            enabled = false;
            break;
          }
        }
        presentation.setEnabled(enabled);
      }
    }
  }

  private final class AssignShortcutAction extends AnAction {
    private final String myActionId;

    public AssignShortcutAction(String actionId) {
      super(AntBundle.message("ant.explorer.assign.shortcut.action.name"));
      myActionId = actionId;
    }

    public void actionPerformed(AnActionEvent e) {
      EditKeymapsDialog dialog = new EditKeymapsDialog(myProject, myActionId);
      dialog.show();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myActionId != null && ActionManager.getInstance().getAction(myActionId) != null);
    }
  }

  private class KeymapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap = null;

    public KeymapListener() {
      KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
      Keymap activeKeymap = keymapManager.getActiveKeymap();
      listenTo(activeKeymap);
      keymapManager.addKeymapManagerListener(this);
    }

    public void activeKeymapChanged(Keymap keymap) {
      listenTo(keymap);
      updateTree();
    }

    private void listenTo(Keymap keymap) {
      if (myCurrentKeymap != null) {
        myCurrentKeymap.removeShortcutChangeListener(this);
      }
      myCurrentKeymap = keymap;
      if (myCurrentKeymap != null) {
        myCurrentKeymap.addShortcutChangeListener(this);
      }
    }

    private void updateTree() {
      myBuilder.updateFromRoot();
    }

    public void onShortcutChanged(String actionId) {
      updateTree();
    }

    public void stopListen() {
      listenTo(null);
      KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(this);
    }
  }
}
