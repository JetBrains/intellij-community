/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.Set;

public class GitLogTreeFileSelectorRenderer extends NodeRenderer {
  private final Set<VirtualFile> myModules;

  public GitLogTreeFileSelectorRenderer(final Project project) {
    myModules = new HashSet<VirtualFile>();

    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      myModules.add(module.getModuleFile());
    }
  }

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        Object element = descriptor.getElement();
        if (element instanceof FileElement) {
          final FileElement fileElement = (FileElement)element;
          final VirtualFile file = fileElement.getFile();
          if (myModules.contains(file)) {
            setIcon(expanded ? Icons.CONTENT_ROOT_ICON_OPEN : Icons.CONTENT_ROOT_ICON_CLOSED);
          }
        }
      }
    }
  }

  /*private static class MyModuleNodeDescriptor extends NodeDescriptor {
    private final NodeDescriptor myProxiedDescriptor;

    private MyModuleNodeDescriptor(final NodeDescriptor proxiedDescriptor) {
      super(proxiedDescriptor.getProject(), proxiedDescriptor.getParentDescriptor());
      myProxiedDescriptor = proxiedDescriptor;

      // a hack
      myName = proxiedDescriptor.toString();
      myOpenIcon = Icons.CONTENT_ROOT_ICON_OPEN;
      myClosedIcon = Icons.CONTENT_ROOT_ICON_CLOSED;
      setChildrenSortingStamp(proxiedDescriptor.getChildrenSortingStamp());
      setIndex(proxiedDescriptor.getIndex());
      setUpdateCount(proxiedDescriptor.getUpdateCount());
      setWasDeclaredAlwaysLeaf(proxiedDescriptor.isWasDeclaredAlwaysLeaf());
    }
    @Override
    public Object getElement() {
      return myProxiedDescriptor.getElement();
    }
    @Override
    public boolean update() {
      return myProxiedDescriptor.update();
    }
  }*/
}
