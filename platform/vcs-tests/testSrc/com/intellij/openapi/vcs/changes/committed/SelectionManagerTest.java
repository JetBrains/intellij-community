/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.containers.Convertor;
import com.intellij.util.treeWithCheckedNodes.SelectionManager;
import com.intellij.util.treeWithCheckedNodes.TreeNodeState;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author irengrig
 */
public class SelectionManagerTest extends PlatformTestCase {
  private FileStructure myFs;
  private SelectionManager myCm;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, IOException>() {
      @Override
      public Void compute() throws IOException {
        myFs = new FileStructure(getProject());
        return null;
      }
    });
    myCm = new SelectionManager(2, 10, MyConvertor.getInstance());
  }

  public void testSimple() {
    assertClear();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    afterMiddle1();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    assertClear();
  }

  public void testSimpleRemove() {
    assertClear();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    afterMiddle1();
    myCm.removeSelection(myFs.myMiddle1);
    assertClear();
  }

  public void testCannotChangeChild() {
    assertClear();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    afterMiddle1();
    myCm.toggleSelection(myFs.getNode(myFs.myInner11));
    afterMiddle1();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    assertClear();

    // and still can change child now
    myCm.toggleSelection(myFs.getNode(myFs.myInner11));
    afterInner11();
    // back to clear
    myCm.toggleSelection(myFs.getNode(myFs.myInner11));
    assertClear();
  }

  private void assertClear() {
    assertNodeState(myFs.myParent, TreeNodeState.CLEAR, true);
  }

  private void afterMiddle1() {
    assertNodeState(myFs.myParent, TreeNodeState.HAVE_SELECTED_BELOW, false);
    assertNodeState(myFs.myMiddle2, TreeNodeState.CLEAR, true);
    assertNodeState(myFs.myMiddle1, TreeNodeState.SELECTED, false);
    assertNodeState(myFs.myInner11, TreeNodeState.HAVE_SELECTED_ABOVE, true);
    assertNodeState(myFs.myInner12, TreeNodeState.HAVE_SELECTED_ABOVE, true);
  }

  private void afterInner11() {
    assertNodeState(myFs.myParent, TreeNodeState.HAVE_SELECTED_BELOW, false);
    assertNodeState(myFs.myMiddle2, TreeNodeState.CLEAR, true);
    assertNodeState(myFs.myMiddle1, TreeNodeState.HAVE_SELECTED_BELOW, false);
    assertNodeState(myFs.myInner12, TreeNodeState.CLEAR, true);

    assertNodeState(myFs.myInner11, TreeNodeState.SELECTED, false);
    assertNodeState(myFs.myLeaf1, TreeNodeState.HAVE_SELECTED_ABOVE, false);
    assertNodeState(myFs.myLeaf2, TreeNodeState.HAVE_SELECTED_ABOVE, false);
  }

  public void testLimit() {
    assertClear();

    myCm.toggleSelection(myFs.getNode(myFs.myInner11));
    myCm.toggleSelection(myFs.getNode(myFs.myInner12));

    Runnable afterTwoMiddle = () -> {
      assertNodeState(myFs.myParent, TreeNodeState.HAVE_SELECTED_BELOW, false);
      assertNodeState(myFs.myMiddle2, TreeNodeState.CLEAR, true);
      assertNodeState(myFs.myMiddle1, TreeNodeState.HAVE_SELECTED_BELOW, false);

      assertNodeState(myFs.myInner12, TreeNodeState.SELECTED, true);

      assertNodeState(myFs.myInner11, TreeNodeState.SELECTED, false);
      assertNodeState(myFs.myLeaf1, TreeNodeState.HAVE_SELECTED_ABOVE, false);
      assertNodeState(myFs.myLeaf2, TreeNodeState.HAVE_SELECTED_ABOVE, false);
    };
    afterTwoMiddle.run();

    // try third
    myCm.toggleSelection(myFs.getNode(myFs.myInner21));
    // get same
    afterTwoMiddle.run();

    // take parent
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    afterMiddle1();
    // clear
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    assertClear();
  }

  public void testCanExpand() {
    assertClear();
    myCm.toggleSelection(myFs.getNode(myFs.myInner11));
    afterInner11();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    afterMiddle1();
    myCm.toggleSelection(myFs.getNode(myFs.myMiddle1));
    assertClear();
  }

  public void testTwoTrees() {
    final Map<VirtualFile, DefaultMutableTreeNode> middle1map = myFs.createNodeMap(myFs.myMiddle1);
    assertClear();
    myCm.toggleSelection(middle1map.get(myFs.myInner11));
    afterInner11(); // though selected in smaller subtree
    myCm.toggleSelection(middle1map.get(myFs.myInner11));
    assertClear();

    myCm.toggleSelection(middle1map.get(myFs.myInner11));
    afterInner11(); // though selected in smaller subtree
    myCm.toggleSelection(middle1map.get(myFs.myMiddle1));
    afterMiddle1(); // though selected in smaller subtree
  }


  private void assertNodeState(@NotNull final VirtualFile vf, final TreeNodeState state, final boolean recursively) {
    Assert.assertNotNull(myFs.getNode(vf));
    Assert.assertEquals(state, myCm.getState(myFs.getNode(vf)));
    // not deep, ok recursion
    if (recursively) {
      for (VirtualFile child : vf.getChildren()) {
        assertNodeState(child, state, true);
      }
    }
  }

  private static class FileStructure {
    private VirtualFile myParent;
    private VirtualFile myMiddle1;
    private VirtualFile myMiddle2;
    private VirtualFile myInner11;
    private VirtualFile myInner12;
    private VirtualFile myInner21;
    private VirtualFile myInner22;
    private VirtualFile myLeaf1;
    private VirtualFile myLeaf2;

    private Map<VirtualFile, DefaultMutableTreeNode> myMap;
    private final Project myProject;

    private FileStructure(final Project project) throws IOException {
      myProject = project;
      final VirtualFile baseDir = project.getBaseDir();

      myParent = baseDir.createChildDirectory(this, "parent");
      myMiddle1 = myParent.createChildDirectory(this, "middle1");
      myMiddle2 = myParent.createChildDirectory(this, "middle2");

      myInner11 = myMiddle1.createChildDirectory(this, "inner11");
      myInner12 = myMiddle1.createChildDirectory(this, "inner12");
      myInner21 = myMiddle2.createChildDirectory(this, "inner21");
      myInner22 = myMiddle2.createChildDirectory(this, "inner22");

      myLeaf1 = myInner11.createChildDirectory(this, "leaf1");
      myLeaf2 = myInner11.createChildDirectory(this, "leaf2");

      myMap = createNodeMap(myParent);
    }

    public DefaultMutableTreeNode getNode(final VirtualFile vf) {
      return myMap.get(vf);
    }

    Map<VirtualFile, DefaultMutableTreeNode> createNodeMap(final VirtualFile parentFile) {
      Map<VirtualFile, DefaultMutableTreeNode> result = new HashMap<>();
      final LinkedList<VirtualFile> queue = new LinkedList<>();
      queue.add(parentFile);
      // for fictive node
      DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(null);
      while (! queue.isEmpty()) {
        final VirtualFile file = queue.removeFirst();
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
        result.put(file, node);

        final DefaultMutableTreeNode parent = result.get(file.getParent());
        parentChild(parent == null ? parentNode : parent, node);

        queue.addAll(Arrays.asList(file.getChildren()));
      }
      return result;
    }

    private void parentChild(final DefaultMutableTreeNode parent, final DefaultMutableTreeNode child) {
      parent.add(child);
      child.setParent(parent);
    }
  }

  private static class MyConvertor implements Convertor<DefaultMutableTreeNode, VirtualFile> {
    private final static MyConvertor ourInstance = new MyConvertor();

    public static MyConvertor getInstance() {
      return ourInstance;
    }

    @Override
    public VirtualFile convert(DefaultMutableTreeNode o) {
      final Object userObject = o.getUserObject();
      return userObject instanceof VirtualFile ? (VirtualFile) userObject : null;
    }
  }
}
