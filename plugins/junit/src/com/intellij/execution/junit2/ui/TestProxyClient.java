/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
