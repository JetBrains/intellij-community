package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.ide.util.treeView.NodeDescriptor;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class TestProxyClient {
  public static TestProxy from(final Object object) {
    if (object instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)object).getUserObject();
      if (userObject instanceof NodeDescriptor) {
        final Object element = ((NodeDescriptor)userObject).getElement();
        if (element instanceof TestProxy) {
          return (TestProxy)element;
        }
      }
    }
    return null;
  }

  public static TestProxy from(final TreePath path) {
    if (path == null) return null;
    return from(path.getLastPathComponent());
  }
}
