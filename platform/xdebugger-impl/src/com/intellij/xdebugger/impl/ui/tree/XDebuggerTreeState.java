package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerTreeState {
  private final NodeInfo myRootInfo;
  private Rectangle myLastVisibleNodeRect;

  private XDebuggerTreeState(@NotNull XDebuggerTree tree) {
    myRootInfo = new NodeInfo("", "", false);
    ApplicationManager.getApplication().assertIsDispatchThread();

    final XDebuggerTreeNode root = (XDebuggerTreeNode)tree.getTreeModel().getRoot();
    if (root != null) {
      addChildren(tree, myRootInfo, root);
    }
  }

  public XDebuggerTreeRestorer restoreState(@NotNull XDebuggerTree tree) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XDebuggerTreeRestorer restorer = new XDebuggerTreeRestorer(tree, myLastVisibleNodeRect);
    restorer.restoreChildren(((XDebuggerTreeNode)tree.getTreeModel().getRoot()), myRootInfo);
    return restorer;
  }

  public static XDebuggerTreeState saveState(@NotNull XDebuggerTree tree) {
    return new XDebuggerTreeState(tree);
  }

  private void addChildren(final XDebuggerTree tree, final NodeInfo nodeInfo, final XDebuggerTreeNode treeNode) {
    if (tree.isExpanded(treeNode.getPath())) {
      List<? extends XDebuggerTreeNode> children = treeNode.getLoadedChildren();
      if (children != null) {
        nodeInfo.myExpanded = true;
        for (XDebuggerTreeNode child : children) {
          final TreePath path = child.getPath();
          final Rectangle bounds = tree.getPathBounds(path);
          if (tree.getVisibleRect().contains(bounds)) {
            myLastVisibleNodeRect = bounds;
          }
          NodeInfo childInfo = createNode(child, tree.isPathSelected(path));
          if (childInfo != null) {
            nodeInfo.addChild(childInfo);
            addChildren(tree, childInfo, child);
          }
        }
      }
    }
  }

  @Nullable
  private static NodeInfo createNode(final XDebuggerTreeNode node, boolean selected) {
    if (node instanceof XValueNodeImpl) {
      XValueNodeImpl valueNode = (XValueNodeImpl)node;
      String name = valueNode.getName();
      String value = valueNode.getValue();
      if (name != null && value != null) {
        return new NodeInfo(name, value, selected);
      }
    }
    return null;
  }

  public static class NodeInfo {
    private final String myName;
    private final String myValue;
    private boolean myExpanded;
    private final boolean mySelected;
    private Map<String, NodeInfo> myChidlren;

    public NodeInfo(final String name, final String value, boolean selected) {
      myName = name;
      myValue = value;
      mySelected = selected;
    }

    public void addChild(@NotNull NodeInfo child) {
      if (myChidlren == null) {
        myChidlren = new HashMap<String, NodeInfo>();
      }
      myChidlren.put(child.myName, child);
    }

    public boolean isExpanded() {
      return myExpanded;
    }

    public String getName() {
      return myName;
    }

    public boolean isSelected() {
      return mySelected;
    }

    public String getValue() {
      return myValue;
    }

    @Nullable
    public NodeInfo getChild(@NotNull String name) {
      return myChidlren != null ? myChidlren.get(name) : null;
    }

    @Nullable
    public NodeInfo removeChild(@NotNull String name) {
      return myChidlren != null ? myChidlren.remove(name) : null;
    }
  }
}
