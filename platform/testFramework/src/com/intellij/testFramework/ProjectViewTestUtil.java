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
package com.intellij.testFramework;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Function;
import junit.framework.Assert;

import java.util.Collection;

public class ProjectViewTestUtil {
  public static VirtualFile[] getFiles(AbstractTreeNode kid, Function<AbstractTreeNode, VirtualFile[]> converterFunction) {
    if (kid instanceof BasePsiNode) {
      Object value = kid.getValue();
      VirtualFile virtualFile = PsiUtilBase.getVirtualFile((PsiElement)value);
      return new VirtualFile[]{virtualFile};
    }
    if (converterFunction != null) {
      final VirtualFile[] result = converterFunction.fun(kid);
      if (result != null) {
        return result;
      }
    }
    return new VirtualFile[0];
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
    MultiValuesMap<VirtualFile, AbstractTreeNode> map = new MultiValuesMap<VirtualFile, AbstractTreeNode>();
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
}
