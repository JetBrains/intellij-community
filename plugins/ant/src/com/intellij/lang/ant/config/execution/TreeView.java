/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.execution;

import com.intellij.ide.DataManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.TextCopyProvider;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModelBase;
import com.intellij.lang.ant.config.AntBuildTargetBase;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.BuildTask;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
  @NonNls public static final String ROOT_TREE_USER_OBJECT = "root";
  @NonNls public static final String JUNIT_TASK_NAME = "junit";

  public TreeView(final Project project, final AntBuildFile buildFile) {
    myProject = project;
    myBuildFile = buildFile;
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return AntConfigurationBase.getInstance(myProject).isAutoScrollToSource();
      }

      protected void setAutoScrollMode(boolean state) {
        AntConfigurationBase.getInstance(myProject).setAutoScrollToSource(state);
      }
    };
    myPanel = createPanel();
  }

  @Override
  public String getId() {
    return "_tree_view_";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private JPanel createPanel() {
    createModel();
    myTree = new MyTree();
    myTree.setLineStyleAngled();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.updateUI();
    myTree.setLargeModel(true);

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(myTree), false);
        }
      }
    });

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(comp, x, y);
      }
    });

    EditSourceOnDoubleClickHandler.install(myTree);

    myAutoScrollToSourceHandler.install(myTree);

    myOccurenceNavigatorSupport = new OccurenceNavigatorSupport(myTree) {
      protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
        if (!(node instanceof MessageNode)) {
          return null;
        }
        MessageNode messageNode = (MessageNode)node;
        AntBuildMessageView.MessageType type = messageNode.getType();

        if (type != AntBuildMessageView.MessageType.MESSAGE && type != AntBuildMessageView.MessageType.ERROR) {
          return null;
        }

        if (!isValid(messageNode.getFile())) {
          return null;
        }

        return new OpenFileDescriptor(myProject, messageNode.getFile(), messageNode.getOffset());
      }

      @Nullable
      public String getNextOccurenceActionName() {
        return AntBundle.message("ant.execution.next.error.warning.action.name");
      }

      @Nullable
      public String getPreviousOccurenceActionName() {
        return AntBundle.message("ant.execution.previous.error.warning.action.name");
      }
    };

    return JBUI.Panels.simplePanel(MessageTreeRenderer.install(myTree));
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

  public void addJavacMessage(AntMessage message, String url) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
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
      addJavacMessageImpl(new AntMessage(message.getType(), message.getPriority(), builder.toString() + message.getText(),
                                         message.getFile(), message.getLine(), message.getColumn()));
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private void addJavacMessageImpl(AntMessage message) {
    MutableTreeNode parentNode = (MutableTreeNode)myParentPath.getLastPathComponent();
    MessageNode messageNode = new MessageNode(message, myProject, false);

    myTreeModel.insertNodeInto(messageNode, parentNode, parentNode.getChildCount());
    myMessageItems.add(messageNode);

    handleExpansion();
  }

  public void addException(AntMessage exception, boolean showFullTrace) {
    MessageNode exceptionRootNode = null;

    StringTokenizer tokenizer = new StringTokenizer(exception.getText(), "\r\n");
    while (tokenizer.hasMoreElements()) {
      String line = (String)tokenizer.nextElement();
      if (exceptionRootNode == null) {
        AntMessage newMessage = new AntMessage(exception.getType(), exception.getPriority(), line, exception.getFile(), exception.getLine(),
                                               exception.getColumn());
        exceptionRootNode = new MessageNode(newMessage, myProject, true);
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

  public void startBuild(AntMessage message) {
  }

  public void buildFailed(AntMessage message) {
    addMessage(message);
  }

  public void startTarget(AntMessage message) {
    collapseTargets();
    MessageNode targetNode = (MessageNode)addMessage(message);
    myParentPath = myParentPath.pathByAddingChild(targetNode);
  }

  private void collapseTargets() {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTreeModel.getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)root.getChildAt(i);
      myTree.collapsePath(new TreePath(node.getPath()));
    }
  }

  public void startTask(AntMessage message) {
    myCurrentTaskName = message.getText();
    MessageNode taskNode = (MessageNode)addMessage(message);
    myParentPath = myParentPath.pathByAddingChild(taskNode);
  }

  private void popupInvoked(Component component, int x, int y) {
    final TreePath path = myTree.getLeadSelectionPath();
    if (path == null) return;
    if (!(path.getLastPathComponent()instanceof MessageNode)) return;
    if (getData(CommonDataKeys.NAVIGATABLE_ARRAY.getName()) == null) return;
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

  @Nullable
  public Object getData(String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      MessageNode item = getSelectedItem();
      if (item == null) return null;
      if (isValid(item.getFile())) {
        return new OpenFileDescriptor(myProject, item.getFile(), item.getOffset());
      }
      if (item.getType() == AntBuildMessageView.MessageType.TARGET) {
        final OpenFileDescriptor descriptor = getDescriptorForTargetNode(item);
        if (descriptor != null && isValid(descriptor.getFile())) {
          return descriptor;
        }
      }
      if (item.getType() == AntBuildMessageView.MessageType.TASK) {
        final OpenFileDescriptor descriptor = getDescriptorForTaskNode(item);
        if (descriptor != null && isValid(descriptor.getFile())) {
          return descriptor;
        }
      }
    }
    return null;
  }

  @Nullable
  private OpenFileDescriptor getDescriptorForTargetNode(MessageNode node) {
    final String targetName = node.getText()[0];
    final AntBuildTargetBase target = (AntBuildTargetBase)myBuildFile.getModel().findTarget(targetName);
    return (target == null) ? null : target.getOpenFileDescriptor();
  }

  private
  @Nullable
  OpenFileDescriptor getDescriptorForTaskNode(MessageNode node) {
    final String[] text = node.getText();
    if (text == null || text.length == 0) return null;
    final String taskName = text[0];
    final TreeNode parentNode = node.getParent();
    if (!(parentNode instanceof MessageNode)) return null;
    final MessageNode messageNode = (MessageNode)parentNode;
    if (messageNode.getType() != AntBuildMessageView.MessageType.TARGET) return null;
    final BuildTask task = ((AntBuildModelBase)myBuildFile.getModel()).findTask(messageNode.getText()[0], taskName);
    return (task == null) ? null : task.getOpenFileDescriptor();
  }

  private static boolean isValid(final VirtualFile file) {
    return file != null && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return file.isValid();
      }
    }).booleanValue();
  }

  public void finishBuild(String messageText) {
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

  public void finishTarget() {
    final TreePath parentPath = myParentPath.getParentPath();
    if (parentPath != null) {
      myParentPath = parentPath;
    }
  }

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
      if (o instanceof MessageNode) {
        MessageNode messageNode = (MessageNode)o;
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
      if (node instanceof MessageNode) {
        MessageNode messageNode = (MessageNode)node;
        String[] text = messageNode.getText();
        if (text.length == 0) continue;
        if (Comparing.equal(treeSelection.mySelectedTarget, text[0])) {
          TreePath pathToSelect = new TreePath(messageNode.getPath());
          for (Enumeration enumeration = messageNode.children(); enumeration.hasMoreElements();) {
            Object o = enumeration.nextElement();
            if (o instanceof MessageNode) {
              messageNode = (MessageNode)o;
              if (Comparing.equal(treeSelection.mySelectedTask, text[0])) {
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

  public String getNextOccurenceActionName() {
    return myOccurenceNavigatorSupport.getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigatorSupport.getPreviousOccurenceActionName();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    return myOccurenceNavigatorSupport.goNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigatorSupport.goPreviousOccurence();
  }

  public boolean hasNextOccurence() {
    return myOccurenceNavigatorSupport.hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myOccurenceNavigatorSupport.hasPreviousOccurence();
  }

  private class MyTree extends Tree implements DataProvider {
    public MyTree() {
      super(myTreeModel);
    }

    public void setRowHeight(int i) {
      super.setRowHeight(0);
      // this is needed in order to make UI calculate the height for each particular row
    }

    public void updateUI() {
      super.updateUI();
      TreeUtil.installActions(this);
    }

    public Object getData(String dataId) {
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
        return new TextCopyProvider() {
          @Nullable
          @Override
          public Collection<String> getTextLinesToCopy() {
            TreePath selection = getSelectionPath();
            Object value = selection == null ? null : selection.getLastPathComponent();
            if (value instanceof MessageNode) {
              MessageNode messageNode = ((MessageNode)value);
              return Arrays.asList(messageNode.getText());
            }
            return value == null ? null : Collections.singleton(value.toString());
          }
        };
      }
      return null;
    }
  }
}

