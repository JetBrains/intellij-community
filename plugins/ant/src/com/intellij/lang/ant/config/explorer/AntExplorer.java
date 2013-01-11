/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.RunManagerAdapter;
import com.intellij.execution.RunManagerEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.AntBuildFilePropertiesAction;
import com.intellij.lang.ant.config.actions.RemoveBuildFileAction;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.config.impl.*;
import com.intellij.lang.ant.config.impl.configuration.BuildFilePropertiesPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AntExplorer extends SimpleToolWindowPanel implements DataProvider, Disposable {
  private Project myProject;
  private AntExplorerTreeBuilder myBuilder;
  private Tree myTree;
  private KeymapListener myKeymapListener;
  private final AntBuildFilePropertiesAction myAntBuildFilePropertiesAction;
  private AntConfiguration myConfig;

  private final TreeExpander myTreeExpander = new TreeExpander() {
    public void expandAll() {
      myBuilder.expandAll();
    }

    public boolean canExpand() {
      final AntConfiguration config = myConfig;
      return config != null && config.getBuildFiles().length != 0;
    }

    public void collapseAll() {
      myBuilder.collapseAll();
    }

    public boolean canCollapse() {
      return canExpand();
    }
  };

  public AntExplorer(final Project project) {
    super(true, true);
    setTransferHandler(new MyTransferHandler());
    myProject = project;
    myConfig = AntConfiguration.getInstance(project);
    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new NodeRenderer());
    myBuilder = new AntExplorerTreeBuilder(project, myTree, model);
    myBuilder.setTargetsFiltered(AntConfigurationBase.getInstance(project).isFilterTargets());
    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        popupInvoked(comp, x, y);
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          runSelection(DataManager.getInstance().getDataContext(myTree));
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    myTree.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        runSelection(DataManager.getInstance().getDataContext(myTree));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);
    myTree.setLineStyleAngled();
    myAntBuildFilePropertiesAction = new AntBuildFilePropertiesAction(this);
    setToolbar(createToolbarPanel());
    setContent(ScrollPaneFactory.createScrollPane(myTree));
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myKeymapListener = new KeymapListener();

    DomManager.getDomManager(project).addDomEventListener(new DomEventListener() {
      public void eventOccured(DomEvent event) {
        myBuilder.queueUpdate();
      }
    }, this);
    RunManagerEx.getInstanceEx(myProject).addRunManagerListener(new RunManagerAdapter() {
      public void beforeRunTasksChanged() {
        myBuilder.queueUpdate();
      }
    });
  }

  public void dispose() {
    final KeymapListener listener = myKeymapListener;
    if (listener != null) {
      myKeymapListener = null;
      listener.stopListen();
    }

    final AntExplorerTreeBuilder builder = myBuilder;
    if (builder != null) {
      Disposer.dispose(builder);
      myBuilder = null;
    }

    final Tree tree = myTree;
    if (tree != null) {
      ToolTipManager.sharedInstance().unregisterComponent(tree);
      for (KeyStroke keyStroke : tree.getRegisteredKeyStrokes()) {
        tree.unregisterKeyboardAction(keyStroke);
      }
      myTree = null;
    }

    myProject = null;
    myConfig = null;
  }

  private JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddAction());
    group.add(new RemoveAction());
    group.add(new RunAction());
    group.add(new ShowAllTargetsAction());
    AnAction action = CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(AntBundle.message("ant.explorer.expand.all.nodes.action.description"));
    group.add(action);
    action = CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(AntBundle.message("ant.explorer.collapse.all.nodes.action.description"));
    group.add(action);
    group.add(myAntBuildFilePropertiesAction);
    group.add(new ContextHelpAction(HelpID.ANT));

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_EXPLORER_TOOLBAR, group, true);
    final JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(actionToolBar.getComponent(), BorderLayout.CENTER);
    return buttonsPanel;
  }

  private void addBuildFile() {
    final FileChooserDescriptor descriptor = createXmlDescriptor();
    descriptor.setTitle(AntBundle.message("select.ant.build.file.dialog.title"));
    descriptor.setDescription(AntBundle.message("select.ant.build.file.dialog.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myProject, null);
    addBuildFile(files);
  }

  private void addBuildFile(final VirtualFile[] files) {
    if (files.length == 0) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final AntConfiguration antConfiguration = myConfig;
        if (antConfiguration == null) {
          return;
        }
        final List<VirtualFile> ignoredFiles = new ArrayList<VirtualFile>();
        for (VirtualFile file : files) {
          try {
            antConfiguration.addBuildFile(file);
          }
          catch (AntNoFileException e) {
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
    });
  }

  public void removeBuildFile() {
    final AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null) {
      return;
    }
    final String fileName = buildFile.getPresentableUrl();
    final int result = Messages.showYesNoDialog(myProject, AntBundle.message("remove.the.reference.to.file.confirmation.text", fileName),
                                                AntBundle.message("confirm.remove.dialog.title"), Messages.getQuestionIcon());
    if (result != 0) {
      return;
    }
    myConfig.removeBuildFile(buildFile);
  }

  public void setBuildFileProperties() {
    final AntBuildFileBase buildFile = getCurrentBuildFile();
    if (buildFile != null && BuildFilePropertiesPanel.editBuildFile(buildFile, myProject)) {
      myConfig.updateBuildFile(buildFile);
      myBuilder.queueUpdate();
      myTree.repaint();
    }
  }

  private void runSelection(final DataContext dataContext) {
    if (!canRunSelection()) {
      return;
    }
    final AntBuildFileBase buildFile = getCurrentBuildFile();
    if (buildFile != null) {
      final TreePath[] paths = myTree.getSelectionPaths();
      final String[] targets = getTargetNamesFromPaths(paths);
      ExecutionHandler.runBuild(buildFile, targets, null, dataContext, Collections.<BuildFileProperty>emptyList(), AntBuildListener.NULL);
    }
  }

  private boolean canRunSelection() {
    if (myTree == null) {
      return false;
    }
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return false;
    }
    final AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null || !buildFile.exists()) {
      return false;
    }
    for (final TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      final AntBuildFileNodeDescriptor buildFileNodeDescriptor;
      if (userObject instanceof AntTargetNodeDescriptor) {
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject();
      }
      else if (userObject instanceof AntBuildFileNodeDescriptor){
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)userObject;
      }
      else {
        buildFileNodeDescriptor = null;
      }
      if (buildFileNodeDescriptor == null || buildFileNodeDescriptor.getBuildFile() != buildFile) {
        return false;
      }
    }
    return true;
  }

  private static String[] getTargetNamesFromPaths(TreePath[] paths) {
    final List<String> targets = new ArrayList<String>();
    for (final TreePath path : paths) {
      final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(userObject instanceof AntTargetNodeDescriptor)) {
        continue;
      }
      final AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getTarget();
      if (target instanceof MetaTarget) {
        ContainerUtil.addAll(targets, ((MetaTarget)target).getTargetNames());
      }
      else {
        targets.add(target.getName());
      }
    }
    return ArrayUtil.toStringArray(targets);
  }

  private static AntBuildTarget[] getTargetObjectsFromPaths(TreePath[] paths) {
    final List<AntBuildTargetBase> targets = new ArrayList<AntBuildTargetBase>();
    for (final TreePath path : paths) {
      final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(userObject instanceof AntTargetNodeDescriptor)) {
        continue;
      }
      final AntBuildTargetBase target = ((AntTargetNodeDescriptor)userObject).getTarget();
      targets.add(target);

    }
    return targets.toArray(new AntBuildTargetBase[targets.size()]);
  }

  public boolean isBuildFileSelected() {
    if( myProject == null) return false;
    final AntBuildFileBase file = getCurrentBuildFile();
    return file != null && file.exists();
  }

  @Nullable
  private AntBuildFileBase getCurrentBuildFile() {
    final AntBuildFileNodeDescriptor descriptor = getCurrentBuildFileNodeDescriptor();
    return (AntBuildFileBase)((descriptor == null) ? null : descriptor.getBuildFile());
  }

  @Nullable
  private AntBuildFileNodeDescriptor getCurrentBuildFileNodeDescriptor() {
    if (myTree == null) {
      return null;
    }
    final TreePath path = myTree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    while (node != null) {
      final Object userObject = node.getUserObject();
      if (userObject instanceof AntBuildFileNodeDescriptor) {
        return (AntBuildFileNodeDescriptor)userObject;
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private void popupInvoked(final Component comp, final int x, final int y) {
    Object userObject = null;
    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null) {
        userObject = node.getUserObject();
      }
    }
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RunAction());
    group.add(new CreateMetaTargetAction());
    group.add(new RemoveMetaTargetsOrBuildFileAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    if (userObject instanceof AntBuildFileNodeDescriptor) {
      group.add(new RemoveBuildFileAction(this));
    }
    if (userObject instanceof AntTargetNodeDescriptor) {
      final AntBuildTargetBase target = ((AntTargetNodeDescriptor)userObject).getTarget();
      final DefaultActionGroup executeOnGroup =
        new DefaultActionGroup(AntBundle.message("ant.explorer.execute.on.action.group.name"), true);
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteBeforeCompilationEvent.getInstance()));
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteAfterCompilationEvent.getInstance()));
      executeOnGroup.addSeparator();
      executeOnGroup.add(new ExecuteBeforeRunAction(target));
      group.add(executeOnGroup);
      group.add(new AssignShortcutAction(target.getActionId()));
    }
    group.add(myAntBuildFilePropertiesAction);
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.ANT_EXPLORER_POPUP, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      final AntBuildFile buildFile = getCurrentBuildFile();
      if (buildFile == null) {
        return null;
      }
      final VirtualFile file = buildFile.getVirtualFile();
      if (file == null) {
        return null;
      }
      final TreePath treePath = myTree.getLeadSelectionPath();
      if (treePath == null) {
        return null;
      }
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      if (node == null) {
        return null;
      }
      if (node.getUserObject() instanceof AntTargetNodeDescriptor) {
        final AntTargetNodeDescriptor targetNodeDescriptor = (AntTargetNodeDescriptor)node.getUserObject();
        final AntBuildTargetBase buildTarget = targetNodeDescriptor.getTarget();
        final OpenFileDescriptor descriptor = buildTarget.getOpenFileDescriptor();
        if (descriptor != null) {
          final VirtualFile descriptorFile = descriptor.getFile();
          if (descriptorFile.isValid()) {
            return descriptor;
          }
        }
      }
      if (file.isValid()) {
        return new OpenFileDescriptor(myProject, file);
      }
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HelpID.ANT;
    }
    else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return myProject != null? myTreeExpander : null;
    }
    else if (PlatformDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return null;
      }
      final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
      for (final TreePath path : paths) {
        for (DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
             node != null;
             node = (DefaultMutableTreeNode)node.getParent()) {
          final Object userObject = node.getUserObject();
          if (!(userObject instanceof AntBuildFileNodeDescriptor)) {
            continue;
          }
          final AntBuildFile buildFile = ((AntBuildFileNodeDescriptor)userObject).getBuildFile();
          if (buildFile != null) {
            final VirtualFile virtualFile = buildFile.getVirtualFile();
            if (virtualFile != null && virtualFile.isValid()) {
              result.add(virtualFile);
            }
          }
          break;
        }
      }
      if (result.size() == 0) {
        return null;
      }
      return VfsUtil.toVirtualFileArray(result);
    }
    return super.getData(dataId);
  }

  public static FileChooserDescriptor createXmlDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, true){
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        boolean b = super.isFileVisible(file, showHiddenFiles);
        if (!file.isDirectory()) {
          b &= StdFileTypes.XML.equals(file.getFileType());
        }
        return b;
      }
    };
  }

  private static final class NodeRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof AntNodeDescriptor) {
        final AntNodeDescriptor descriptor = (AntNodeDescriptor)userObject;
        descriptor.customize(this);
      }
      else {
        append(tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private final class AddAction extends AnAction {
    public AddAction() {
      super(AntBundle.message("add.ant.file.action.name"), AntBundle.message("add.ant.file.action.description"), IconUtil.getAddIcon());
    }

    public void actionPerformed(AnActionEvent e) {
      addBuildFile();
    }
  }

  private final class RemoveAction extends AnAction {
    public RemoveAction() {
      super(AntBundle.message("remove.ant.file.action.name"), AntBundle.message("remove.ant.file.action.description"),
            IconUtil.getRemoveIcon());
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
            AllIcons.Actions.Execute);
    }

    public void actionPerformed(AnActionEvent e) {
      runSelection(e.getDataContext());
    }

    public void update(AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      final String place = event.getPlace();
      if (ActionPlaces.ANT_EXPLORER_TOOLBAR.equals(place)) {
        presentation.setText(AntBundle.message("run.ant.file.or.target.action.name"));
      }
      else {
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null && paths.length == 1 &&
            ((DefaultMutableTreeNode)paths[0].getLastPathComponent()).getUserObject() instanceof AntBuildFileNodeDescriptor) {
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
            AllIcons.General.Filter);
    }

    public boolean isSelected(AnActionEvent event) {
      final Project project = myProject;
      return project != null? AntConfigurationBase.getInstance(project).isFilterTargets() : false;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      setTargetsFiltered(flag);
    }
  }

  private void setTargetsFiltered(boolean value) {
    myBuilder.setTargetsFiltered(value);
    AntConfigurationBase.getInstance(myProject).setFilterTargets(value);
  }

  private final class ExecuteOnEventAction extends ToggleAction {
    private final AntBuildTargetBase myTarget;
    private final ExecutionEvent myExecutionEvent;

    public ExecuteOnEventAction(final AntBuildTargetBase target, final ExecutionEvent executionEvent) {
      super(executionEvent.getPresentableName());
      myTarget = target;
      myExecutionEvent = executionEvent;
    }

    public boolean isSelected(AnActionEvent e) {
      return myTarget.equals(AntConfigurationBase.getInstance(myProject).getTargetForEvent(myExecutionEvent));
    }

    public void setSelected(AnActionEvent event, boolean state) {
      final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
      if (state) {
        final AntBuildFileBase buildFile =
          (AntBuildFileBase)((myTarget instanceof MetaTarget) ? ((MetaTarget)myTarget).getBuildFile() : myTarget.getModel().getBuildFile());
        antConfiguration.setTargetForEvent(buildFile, myTarget.getName(), myExecutionEvent);
      }
      else {
        antConfiguration.clearTargetForEvent(myExecutionEvent);
      }
      myBuilder.queueUpdate();
    }

    public void update(AnActionEvent e) {
      super.update(e);
      final AntBuildFile buildFile = myTarget.getModel().getBuildFile();
      e.getPresentation().setEnabled(buildFile != null && buildFile.exists());
    }
  }

  private final class ExecuteBeforeRunAction extends AnAction {
    private final AntBuildTarget myTarget;

    public ExecuteBeforeRunAction(final AntBuildTarget target) {
      super(AntBundle.message("executes.before.run.debug.acton.name"));
      myTarget = target;
    }

    public void actionPerformed(AnActionEvent e) {
      final AntExecuteBeforeRunDialog dialog = new AntExecuteBeforeRunDialog(myProject, myTarget);
      dialog.show();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myTarget.getModel().getBuildFile().exists());
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
      final ExecuteCompositeTargetEvent event = new ExecuteCompositeTargetEvent(targets);
      final SaveMetaTargetDialog dialog = new SaveMetaTargetDialog(myTree, event, AntConfigurationBase.getInstance(myProject), buildFile);
      dialog.setTitle(e.getPresentation().getText());
      dialog.show();
      if (dialog.isOK()) {
        myBuilder.queueUpdate();
        myTree.repaint();
      }
    }

    public void update(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      e.getPresentation().setEnabled(paths != null && paths.length > 1 && canRunSelection());
    }
  }

  private final class RemoveMetaTargetsOrBuildFileAction extends AnAction {

    public RemoveMetaTargetsOrBuildFileAction() {
      super(AntBundle.message("remove.meta.targets.action.name"), AntBundle.message("remove.meta.targets.action.description"), null);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)), myTree);
      Disposer.register(AntExplorer.this, new Disposable() {
        public void dispose() {
          RemoveMetaTargetsOrBuildFileAction.this.unregisterCustomShortcutSet(myTree);
        }
      });
      myTree.registerKeyboardAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          doAction();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
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
          if (node.getUserObject() instanceof AntBuildFileNodeDescriptor) {
            final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
            if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
              removeBuildFile();
              return;
            }
          }
        }
        // try to remove meta targets
        final AntBuildTarget[] targets = getTargetObjectsFromPaths(paths);
        final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
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
        myBuilder.queueUpdate();
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
        if (node.getUserObject() instanceof AntBuildFileNodeDescriptor) {
          final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
          if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
            text = AntBundle.message("remove.selected.build.file.action.name");
            enabled = true;
          }
        }
        else {
          if (node.getUserObject() instanceof AntTargetNodeDescriptor) {
            final AntTargetNodeDescriptor descr = (AntTargetNodeDescriptor)node.getUserObject();
            final AntBuildTargetBase target = descr.getTarget();
            if (target instanceof MetaTarget) {
              enabled = true;
            }
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
      new EditKeymapsDialog(myProject, myActionId).show();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myActionId != null && ActionManager.getInstance().getAction(myActionId) != null);
    }
  }

  private class KeymapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap = null;

    public KeymapListener() {
      final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
      final Keymap activeKeymap = keymapManager.getActiveKeymap();
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

  private final class MyTransferHandler extends TransferHandler {

    @Override
    public boolean importData(final TransferSupport support) {
      if (canImport(support)) {
        addBuildFile(getAntFiles(support));
        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(final TransferSupport support) {
      return FileCopyPasteUtil.isFileListFlavorSupported(support.getDataFlavors());
    }

    private VirtualFile[] getAntFiles(final TransferSupport support) {
      List<VirtualFile> virtualFileList = new ArrayList<VirtualFile>();
      final List<File> fileList = FileCopyPasteUtil.getFileList(support.getTransferable());
      if (fileList != null) {
        for (File file : fileList ) {
          ContainerUtil.addIfNotNull(virtualFileList, VfsUtil.findFileByIoFile(file, true));
        }
      }

      return VfsUtil.toVirtualFileArray(virtualFileList);
    }
  }
}
