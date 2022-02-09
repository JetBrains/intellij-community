// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.ui.tree.TreeVisitor.Action;
import com.intellij.util.Function;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.Comparator;

public final class ProjectViewTestUtil {
  public static VirtualFile[] getFiles(AbstractTreeNode<?> kid, Function<? super AbstractTreeNode<?>, VirtualFile[]> converterFunction) {
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
                             MultiValuesMap<VirtualFile, AbstractTreeNode<?>> map,
                             AbstractTreeStructure structure,
                             Function<? super AbstractTreeNode<?>, VirtualFile[]> converterFunction) {
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
                                         Function<? super AbstractTreeNode<?>, VirtualFile[]> converterFunction) {
    MultiValuesMap<VirtualFile, AbstractTreeNode<?>> map = new MultiValuesMap<>();
    collect((AbstractTreeNode)rootElement, map, structure, converterFunction);

    for (VirtualFile eachFile : map.keySet()) {
      Collection<AbstractTreeNode<?>> nodes = map.values();
      for (final AbstractTreeNode node : nodes) {
        ProjectViewNode eachNode = (ProjectViewNode)node;
        boolean actual = eachNode.contains(eachFile);
        boolean expected = map.get(eachFile).contains(eachNode);
        if (actual != expected) {
          Assert.fail("file=" + eachFile + "\n node=" + eachNode.getTestPresentation() + " expected:" + expected);
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
    Assert.assertEquals(expected.trim(), actual.trim());
  }

  public static boolean isExpanded(PsiElement element, AbstractProjectViewPSIPane pane) {
    return null != getVisiblePath(element, pane);
  }

  public static @Nullable TreePath getVisiblePath(@NotNull PsiElement element, @NotNull AbstractProjectViewPSIPane pane) {
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    return TreeUtil.visitVisibleRows(pane.getTree(), path -> {
      AbstractTreeNode<?> node = TreeUtil.getLastUserObject(AbstractTreeNode.class, path);
      return node != null && element.equals(node.getValue()) ? Action.INTERRUPT : Action.CONTINUE;
    });
  }

  public static void setupImpl(@NotNull Project project, boolean loadPaneExtensions) {
    ToolWindowHeadlessManagerImpl toolWindowManager = (ToolWindowHeadlessManagerImpl)ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW);

    if (toolWindow == null) {
      for (ToolWindowEP bean : ToolWindowEP.EP_NAME.getExtensionList()) {
        if (bean.id.equals(ToolWindowId.PROJECT_VIEW)) {
          toolWindow = toolWindowManager.doRegisterToolWindow(bean.id);
          break;
        }
      }
    }

    assert toolWindow != null;
    LafManager.getInstance();
    ((ProjectViewImpl)ProjectView.getInstance(project)).setupImpl(toolWindow, loadPaneExtensions);
  }
}
