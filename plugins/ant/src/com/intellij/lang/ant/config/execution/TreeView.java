// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.TextCopyProvider;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;

public final class TreeView implements AntOutputView, OccurenceNavigator {
  private Tree myTree;
  private DefaultTreeModel myTreeModel;
  private TreePath myParentPath = null;
  private final ArrayList<MessageNode> myMessageItems = new ArrayList<>();
  private final JPanel myPanel;
  private boolean myActionsEnabled = true;
  private String myCurrentTaskName;

  private final Project myProject;
  private final AntBuildFile myBuildFile;
  private DefaultMutableTreeNode myStatusNode;
  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private OccurenceNavigatorSupport myOccurenceNavigatorSupport;
  private final boolean myAutoCollapseTargets;
  @NonNls public static final String ROOT_TREE_USER_OBJECT = "root";
  @NonNls public static final String JUNIT_TASK_NAME = "junit";

  public TreeView(final Project project, final AntBuildFile buildFile) {
    myProject = project;
    myBuildFile = buildFile;
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return AntConfigurationBase.getInstance(myProject).isAutoScrollToSource();
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        AntConfigurationBase.getInstance(myProject).setAutoScrollToSource(state);
      }
    };
    myPanel = createPanel();
    myAutoCollapseTargets = buildFile instanceof AntBuildFileBase && ((AntBuildFileBase)myBuildFile).isCollapseFinishedTargets();
  }

  @Override
  public String getId() {
    return "_tree_view_";
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private JPanel createPanel() {
    createModel();
    myTree = new MyTree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.updateUI();
    myTree.setLargeModel(true);

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);

    myAutoScrollToSourceHandler.install(myTree);

    myOccurenceNavigatorSupport = new OccurenceNavigatorSupport(myTree) {
      @Override
      protected Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
        if (!(node instanceof MessageNode messageNode)) {
          return null;
        }
        AntBuildMessageView.MessageType type = messageNode.getType();

        if (type != AntBuildMessageView.MessageType.MESSAGE && type != AntBuildMessageView.MessageType.ERROR) {
          return null;
        }

        if (!isValid(messageNode.getFile())) {
          return null;
        }

        return PsiNavigationSupport.getInstance()
                                   .createNavigatable(myProject, messageNode.getFile(), messageNode.getOffset());
      }

      @NotNull
      @Override
      public String getNextOccurenceActionName() {
        return AntBundle.message("ant.execution.next.error.warning.action.name");
      }

      @NotNull
      @Override
      public String getPreviousOccurenceActionName() {
        return AntBundle.message("ant.execution.previous.error.warning.action.name");
      }
    };

    final JScrollPane treePane = MessageTreeRenderer.install(myTree);
    if (myBuildFile instanceof AntBuildFileBase) {
      ((MessageTreeRenderer)myTree.getCellRenderer()).setUseAnsiColor(
        ((AntBuildFileBase)myBuildFile).isColoredOutputMessages()
      );
    }

    return JBUI.Panels.simplePanel(treePane);
  }

  private void createModel() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(ROOT_TREE_USER_OBJECT);
    myTreeModel = new DefaultTreeModel(rootNode);
    myParentPath = new TreePath(rootNode);
  }

  public void setActionsEnabled(boolean actionsEnabled) {
    myActionsEnabled = actionsEnabled;
    if (actionsEnabled) {
      myTreeModel.reload();
    }
  }

  @Override
  public Object addMessage(AntMessage message) {
    MessageNode messageNode = createMessageNode(message);

    MutableTreeNode parentNode = (MutableTreeNode)myParentPath.getLastPathComponent();
    myTreeModel.insertNodeInto(messageNode, parentNode, parentNode.getChildCount());
    myMessageItems.add(messageNode);

    handleExpansion();
    return messageNode;
  }

  public void addMessages(AntMessage[] messages) {
    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)myParentPath.getLastPathComponent();
    int[] indices = new int[messages.length];
    for (int i = 0; i < messages.length; i++) {
      AntMessage message = messages[i];
      MessageNode messageNode = createMessageNode(message);
      indices[i] = parentNode.getChildCount();
      parentNode.insert(messageNode, indices[i]);
      myMessageItems.add(messageNode);
    }
    myTreeModel.nodesWereInserted(parentNode, indices);
    handleExpansion();
  }

  private MessageNode createMessageNode(AntMessage message) {
    String text = message.getText();

    boolean allowToShowPosition = true;
    if (JUNIT_TASK_NAME.equals(myCurrentTaskName)) {
      HyperlinkUtil.PlaceInfo info = HyperlinkUtil.parseJUnitMessage(myProject, text);
      if (info != null) {
        message = new AntMessage(message.getType(), message.getPriority(), text, info.getFile(), 1, 1);
        allowToShowPosition = false;
      }
    }

    return new MessageNode(message, myProject, allowToShowPosition);
  }

  private void handleExpansion() {
    if (myActionsEnabled && !myTree.hasBeenExpanded(myParentPath)) {
      myTree.expandPath(myParentPath);
    }
  }

  void scrollToLastMessage() {
    if (myTree == null) return;
    int count = myTree.getRowCount();
    if (count > 0) {
      int row = count - 1;
      TreeUtil.selectPath(myTree, myTree.getPathForRow(row));
    }
  }

  @Override
  public void addJavacMessage(AntMessage message, @NlsSafe String url) {
    final String messagePrefix = printMessage(message, url);
    addJavacMessageImpl(message.withText(messagePrefix + message.getText()));
  }

  @NotNull
  static @NlsSafe String printMessage(@NotNull AntMessage message, @NlsSafe String url) {
    final StringBuilder builder = new StringBuilder();
    final VirtualFile file = message.getFile();
    if (message.getLine() > 0) {
      if (file != null) {
        ApplicationManager.getApplication().runReadAction(() -> {
          String presentableUrl = file.getPresentableUrl();
          builder.append(presentableUrl);
          builder.append(' ');
        });
      }
      else if (url != null) {
        builder.append(url);
        builder.append(' ');
      }
      builder.append('(');
      builder.append(message.getLine());
      builder.append(':');
      builder.append(message.getColumn());
      builder.append(") ");
    }
    return builder.toString();
  }

  private void addJavacMessageImpl(AntMessage message) {
    MutableTreeNode parentNode = (MutableTreeNode)myParentPath.getLastPathComponent();
    MessageNode messageNode = new MessageNode(message, myProject, false);

    myTreeModel.insertNodeInto(messageNode, parentNode, parentNode.getChildCount());
    myMessageItems.add(messageNode);

    handleExpansion();
  }

  @Override
  public void addException(AntMessage exception, boolean showFullTrace) {
    MessageNode exceptionRootNode = null;

    StringTokenizer tokenizer = new StringTokenizer(exception.getText(), "\r\n");
    while (tokenizer.hasMoreElements()) {
      String line = (String)tokenizer.nextElement();
      if (exceptionRootNode == null) {
        exceptionRootNode = new MessageNode(exception.withText(line), myProject, true);
        myMessageItems.add(exceptionRootNode);
      }
      else if (showFullTrace) {
        if (StringUtil.startsWithChar(line, '\t')) {
          line = line.substring(1);
        }

        HyperlinkUtil.PlaceInfo info = HyperlinkUtil.parseStackLine(myProject, '\t' + line);
        VirtualFile file = info != null ? info.getFile() : null;
        int lineNumber = info != null ? info.getLine() : 0;
        int column = info != null ? info.getColumn() : 1;
        AntMessage newMessage = new AntMessage(exception.getType(), exception.getPriority(), line, file, lineNumber, column);
        MessageNode child = new MessageNode(newMessage, myProject, false);
        exceptionRootNode.add(child);
        myMessageItems.add(child);
      }
    }
    if (exceptionRootNode == null) return;

    MutableTreeNode parentNode = (MutableTreeNode)myParentPath.getLastPathComponent();
    myTreeModel.insertNodeInto(exceptionRootNode, parentNode, parentNode.getChildCount());

    handleExpansion();
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 2);
  }

  public void expandAll() {
    TreePath[] selectionPaths = myTree.getSelectionPaths();
    TreePath leadSelectionPath = myTree.getLeadSelectionPath();
    int row = 0;
    while (row < myTree.getRowCount()) {
      myTree.expandRow(row);
      row++;
    }

    if (selectionPaths != null) {
      // restore selection
      myTree.setSelectionPaths(selectionPaths);
    }
    if (leadSelectionPath != null) {
      // scroll to lead selection path
      myTree.scrollPathToVisible(leadSelectionPath);
    }
  }

  public void clearAllMessages() {
    for (MessageNode messageItem : myMessageItems) {
      messageItem.clearRangeMarker();
    }
    myMessageItems.clear();
    myStatusNode = null;
    createModel();
    myTree.setModel(myTreeModel);
  }

  @Override
  public void startBuild(AntMessage message) {
  }

  @Override
  public void buildFailed(AntMessage message) {
    addMessage(message);
  }

  @Override
  public void startTarget(AntMessage message) {
    collapseTargets();
    MessageNode targetNode = (MessageNode)addMessage(message);
    myParentPath = myParentPath.pathByAddingChild(targetNode);
  }

  private void collapseTargets() {
    if (myAutoCollapseTargets) {
      final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
      for (int i = 0; i < root.getChildCount(); i++) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)root.getChildAt(i);
        myTree.collapsePath(new TreePath(node.getPath()));
      }
    }
  }

  @Override
  public void startTask(AntMessage message) {
    myCurrentTaskName = message.getText();
    MessageNode taskNode = (MessageNode)addMessage(message);
    myParentPath = myParentPath.pathByAddingChild(taskNode);
  }

  private void popupInvoked(Component component, int x, int y) {
    TreePath path = myTree.getLeadSelectionPath();
    if (path == null) return;
    if (!(path.getLastPathComponent() instanceof MessageNode)) return;
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.ANT_MESSAGES_POPUP, group);
    menu.getComponent().show(component, x, y);
  }

  @Nullable
  private MessageNode getSelectedItem() {
    TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    if (!(path.getLastPathComponent()instanceof MessageNode)) return null;
    return (MessageNode)path.getLastPathComponent();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    MessageNode item = getSelectedItem();
    if (item == null) return;
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      if (isValid(item.getFile())) {
        return PsiNavigationSupport.getInstance().createNavigatable(myProject, item.getFile(), item.getOffset());
      }
      if (item.getType() == AntBuildMessageView.MessageType.TARGET) {
        final Navigatable descriptor = getDescriptorForTargetNode(item);
        if (descriptor != null && descriptor.canNavigate()) {
          return descriptor;
        }
      }
      if (item.getType() == AntBuildMessageView.MessageType.TASK) {
        final Navigatable descriptor = getDescriptorForTaskNode(item);
        if (descriptor != null && descriptor.canNavigate()) {
          return descriptor;
        }
      }
      return null;
    });
  }

  @Nullable
  private Navigatable getDescriptorForTargetNode(MessageNode node) {
    final String targetName = node.getText()[0];
    final AntBuildTargetBase target = (AntBuildTargetBase)myBuildFile.getModel().findTarget(targetName);
    return (target == null) ? null : target.getOpenFileDescriptor();
  }

  private
  @Nullable
  Navigatable getDescriptorForTaskNode(MessageNode node) {
    final String[] text = node.getText();
    if (text == null || text.length == 0) return null;
    final String taskName = text[0];
    final TreeNode parentNode = node.getParent();
    if (!(parentNode instanceof MessageNode messageNode)) return null;
    if (messageNode.getType() != AntBuildMessageView.MessageType.TARGET) return null;
    final BuildTask task = ((AntBuildModelBase)myBuildFile.getModel()).findTask(messageNode.getText()[0], taskName);
    return (task == null) ? null : task.getOpenFileDescriptor();
  }

  private static boolean isValid(final VirtualFile file) {
    return file != null && ReadAction.compute(() -> file.isValid()).booleanValue();
  }

  @Override
  public void finishBuild(@Nls String messageText) {
    collapseTargets();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
    myStatusNode = new DefaultMutableTreeNode(messageText);
    myTreeModel.insertNodeInto(myStatusNode, root, root.getChildCount());
  }

  public void scrollToStatus() {
    if (myStatusNode != null) {
      TreeUtil.selectPath(myTree, new TreePath(myStatusNode.getPath()));
    }
  }

  @Override
  public void finishTarget() {
    final TreePath parentPath = myParentPath.getParentPath();
    if (parentPath != null) {
      myParentPath = parentPath;
    }
  }

  @Override
  public void finishTask() {
    myCurrentTaskName = null;
    final TreePath parentPath = myParentPath.getParentPath();
    if (parentPath != null) {
      myParentPath = parentPath;
    }
  }

  @Nullable
  private static TreePath getFirstErrorPath(TreePath treePath) {
    TreeNode treeNode = (TreeNode)treePath.getLastPathComponent();
    if (treeNode instanceof MessageNode) {
      AntBuildMessageView.MessageType type = ((MessageNode)treeNode).getType();
      if (type == AntBuildMessageView.MessageType.ERROR) {
        return treePath;
      }
    }

    if (treeNode.getChildCount() == 0) {
      return null;
    }
    for (int i = 0; i < treeNode.getChildCount(); i++) {
      TreeNode childNode = treeNode.getChildAt(i);
      TreePath childPath = treePath.pathByAddingChild(childNode);
      TreePath usagePath = getFirstErrorPath(childPath);
      if (usagePath != null) {
        return usagePath;
      }
    }
    return null;
  }

  public void scrollToFirstError() {
    TreePath path = getFirstErrorPath(new TreePath(myTreeModel.getRoot()));
    if (path != null) {
      TreeUtil.selectPath(myTree, path);
    }
  }

  public static final class TreeSelection {
    public String mySelectedTarget;
    public String mySelectedTask;

    public boolean isEmpty() {
      return mySelectedTarget == null && mySelectedTask == null;
    }
  }

  public TreeSelection getSelection() {
    TreeSelection selection = new TreeSelection();

    TreePath path = myTree.getSelectionPath();
    if (path == null) return selection;

    Object[] paths = path.getPath();
    for (Object o : paths) {
      if (o instanceof MessageNode messageNode) {
        AntBuildMessageView.MessageType type = messageNode.getType();
        if (type == AntBuildMessageView.MessageType.TARGET) {
          selection.mySelectedTarget = messageNode.getText()[0];
        }
        else if (type == AntBuildMessageView.MessageType.TASK) {
          selection.mySelectedTask = messageNode.getText()[0];
        }
      }
    }

    return selection;
  }

  public boolean restoreSelection(TreeSelection treeSelection) {
    if (treeSelection.isEmpty()) return false;

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      TreeNode node = root.getChildAt(i);
      if (node instanceof MessageNode messageNode) {
        String[] text = messageNode.getText();
        if (text.length == 0) continue;
        if (Objects.equals(treeSelection.mySelectedTarget, text[0])) {
          TreePath pathToSelect = new TreePath(messageNode.getPath());
          for (Enumeration enumeration = messageNode.children(); enumeration.hasMoreElements();) {
            Object o = enumeration.nextElement();
            if (o instanceof MessageNode) {
              messageNode = (MessageNode)o;
              if (Objects.equals(treeSelection.mySelectedTask, text[0])) {
                pathToSelect = new TreePath(messageNode.getPath());
                break;
              }
            }
          }
          TreeUtil.selectPath(myTree, pathToSelect);
          myTree.expandPath(pathToSelect);
          return true;
        }
      }
    }

    return false;
  }

  ToggleAction createToggleAutoscrollAction() {
    return myAutoScrollToSourceHandler.createToggleAction();
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigatorSupport.getNextOccurenceActionName();
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  @Override
  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigatorSupport.goNextOccurence();
  }

  @Override
  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigatorSupport.goPreviousOccurence();
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigatorSupport.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigatorSupport.hasPreviousOccurence();
  }

  private class MyTree extends Tree implements UiDataProvider {
    MyTree() {
      super(myTreeModel);
    }

    @Override
    public void setRowHeight(int i) {
      super.setRowHeight(0);
      // this is needed in order to make UI calculate the height for each particular row
    }

    @Override
    public void updateUI() {
      super.updateUI();
      TreeUtil.installActions(this);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(PlatformDataKeys.COPY_PROVIDER, new TextCopyProvider() {
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Nullable
        @Override
        public Collection<String> getTextLinesToCopy() {
          TreePath selection = getSelectionPath();
          Object value = selection == null ? null : selection.getLastPathComponent();
          if (value instanceof MessageNode messageNode) {
            return Arrays.asList(messageNode.getText());
          }
          return value == null ? null : Collections.singleton(value.toString());
        }
      });
    }
  }
}

