/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.Comparator;

public class ProjectViewTestUtil {
  public static VirtualFile[] getFiles(AbstractTreeNode kid, Function<AbstractTreeNode, VirtualFile[]> converterFunction) {
    if (kid instanceof BasePsiNode) {
      Object value = kid.getValue();
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile((PsiElement)value);
      return new VirtualFile[]{virtualFile};
    }
    if (converterFunction != null) {
      final VirtualFile[] result = converterFunction.fun(kid);
      if (result != null) {
        return result;
      }
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  public static void collect(AbstractTreeNode node,
                              MultiValuesMap<VirtualFile, AbstractTreeNode> map,
                              final AbstractTreeStructure structure,
                              Function<AbstractTreeNode, VirtualFile[]> converterFunction) {
    Object[] kids = structure.getChildElements(node);
    for (Object kid1 : kids) {
      ProjectViewNode kid = (ProjectViewNode)kid1;
      final VirtualFile[] files = getFiles(kid, converterFunction);
      for (VirtualFile vFile : files) {
        map.put(vFile, kid);
        ProjectViewNode eachParent = (ProjectViewNode)kid.getParent();
        while (eachParent != null) {
          map.put(vFile, eachParent);
          eachParent = (ProjectViewNode)eachParent.getParent();
        }

      }
      collect(kid, map, structure, converterFunction);
    }
  }

  public static void checkContainsMethod(final Object rootElement,
                                         final AbstractTreeStructure structure,
                                         Function<AbstractTreeNode, VirtualFile[]> converterFunction) {
    MultiValuesMap<VirtualFile, AbstractTreeNode> map = new MultiValuesMap<>();
    collect((AbstractTreeNode)rootElement, map, structure, converterFunction);

    for (VirtualFile eachFile : map.keySet()) {
      Collection<AbstractTreeNode> nodes = map.values();
      for (final AbstractTreeNode node : nodes) {
        ProjectViewNode eachNode = (ProjectViewNode)node;
        boolean actual = eachNode.contains(eachFile);
        boolean expected = map.get(eachFile).contains(eachNode);
        if (actual != expected) {
          boolean actual1 = eachNode.contains(eachFile);
          boolean expected1 = map.get(eachFile).contains(eachNode);

          Assert.assertTrue("file=" + eachFile + " node=" + eachNode.getTestPresentation() + " expected:" + expected, false);
        }
      }
    }
  }

  public static void checkGetParentConsistency(AbstractTreeStructure structure, Object from) {
    Object[] childElements = structure.getChildElements(from);
    for (Object childElement : childElements) {
      Assert.assertSame(from, structure.getParentElement(childElement));
      checkGetParentConsistency(structure, childElement);
    }
  }

  public static void assertStructureEqual(AbstractTreeStructure structure,
                                      String expected,
                                      @Nullable Queryable.PrintInfo printInfo) {
    assertStructureEqual(structure, expected, 27, null, structure.getRootElement(), printInfo);
  }

  public static void assertStructureEqual(AbstractTreeStructure structure,
                                          String expected,
                                          int maxRowCount,
                                          @Nullable Comparator comparator,
                                          Object rootNode,
                                          @Nullable Queryable.PrintInfo printInfo) {
    checkGetParentConsistency(structure, rootNode);
    String actual = PlatformTestUtil.print(structure, rootNode, 0, comparator, maxRowCount, ' ', printInfo).toString();
    Assert.assertEquals(expected, actual);
  }

  protected static boolean isExpanded(DefaultMutableTreeNode nodeForElement, AbstractProjectViewPSIPane pane) {
    TreePath path = new TreePath(nodeForElement.getPath());
    return pane.getTree().isExpanded(path.getParentPath());
  }

  public static DefaultMutableTreeNode getNodeForElement(PsiElement element, AbstractProjectViewPSIPane pane) {
    JTree tree = pane.getTree();
    TreeModel model = tree.getModel();
    Object root = model.getRoot();
    return getNodeForElement(root, model, element);
  }

  private static DefaultMutableTreeNode getNodeForElement(Object root, TreeModel model, PsiElement element) {
    if (root instanceof DefaultMutableTreeNode) {
      Object userObject = ((DefaultMutableTreeNode)root).getUserObject();
      if (userObject instanceof AbstractTreeNode) {
        AbstractTreeNode treeNode = (AbstractTreeNode)userObject;
        if (element.equals(treeNode.getValue())) return (DefaultMutableTreeNode)root;
        for (int i = 0; i < model.getChildCount(root); i++) {
          DefaultMutableTreeNode nodeForChild = getNodeForElement(model.getChild(root, i), model, element);
          if (nodeForChild != null) return nodeForChild;
        }
      }
    }
    return null;
  }

  public static boolean isExpanded(PsiElement element, AbstractProjectViewPSIPane pane) {
    DefaultMutableTreeNode nodeForElement = getNodeForElement(element, pane);
    return nodeForElement != null && isExpanded((DefaultMutableTreeNode)nodeForElement.getParent(), pane);
  }

  public static void setupImpl(@NotNull Project project, boolean loadPaneExtensions) {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);

    if (toolWindow == null) {
      ToolWindowEP[] beans = Extensions.getExtensions(ToolWindowEP.EP_NAME);
      for (final ToolWindowEP bean : beans) {
        if (bean.id.equals(ToolWindowId.PROJECT_VIEW)) {
          toolWindow = toolWindowManager.registerToolWindow(bean.id, new JLabel(), ToolWindowAnchor.fromText(bean.anchor), project,
                                                            false, bean.canCloseContents);
          break;
        }
      }
    }

    ((ProjectViewImpl)ProjectView.getInstance(project)).setupImpl(toolWindow, loadPaneExtensions);
  }
}
