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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertNotNull;

public class ProjectViewFixture extends ToolWindowFixture {
  ProjectViewFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Project", project, robot);
  }

  @NotNull
  public PaneFixture selectProjectPane() {
    activate();
    final ProjectView projectView = ProjectView.getInstance(myProject);
    pause(new Condition("Project view is initialized") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return field("isInitialized").ofType(boolean.class).in(projectView).get();
      }
    }, SHORT_TIMEOUT);

    final String id = "ProjectPane";
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        projectView.changeView(id);
      }
    });
    return new PaneFixture(projectView.getProjectViewPaneById(id));
  }

  @NotNull
  public PaneFixture selectAndroidPane() {
    activate();
    final ProjectView projectView = ProjectView.getInstance(myProject);
    pause(new Condition("Project view is initialized") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return field("isInitialized").ofType(boolean.class).in(projectView).get();
      }
    }, SHORT_TIMEOUT);

    final String id = "AndroidView";
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        projectView.changeView(id);
      }
    });
    return new PaneFixture(projectView.getProjectViewPaneById(id));
  }

  public class PaneFixture {
    @NotNull private final AbstractProjectViewPane myPane;

    PaneFixture(@NotNull AbstractProjectViewPane pane) {
      myPane = pane;
    }

    @NotNull
    public PaneFixture expand() {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          TreeUtil.expandAll(myPane.getTree());
        }
      });
      return this;
    }

    @NotNull
    private AbstractTreeStructure getTreeStructure() {
      final AtomicReference<AbstractTreeStructure> treeStructureRef = new AtomicReference<AbstractTreeStructure>();
      pause(new Condition("Tree Structure to be built") {
        @Override
        public boolean test() {
          AbstractTreeStructure treeStructure = GuiActionRunner.execute(new GuiQuery<AbstractTreeStructure>() {
            @Override
            protected AbstractTreeStructure executeInEDT() throws Throwable {
              try {
                return myPane.getTreeBuilder().getTreeStructure();
              }
              catch (NullPointerException e) {
                // expected;
              }
              return null;
            }
          });
          treeStructureRef.set(treeStructure);
          return treeStructure != null;
        }
      }, SHORT_TIMEOUT);

      return treeStructureRef.get();
    }

    @NotNull
    public NodeFixture findExternalLibrariesNode() {
      final AbstractTreeStructure treeStructure = getTreeStructure();

      ExternalLibrariesNode node = GuiActionRunner.execute(new GuiQuery<ExternalLibrariesNode>() {
        @Nullable
        @Override
        protected ExternalLibrariesNode executeInEDT() throws Throwable {
          Object[] childElements = treeStructure.getChildElements(treeStructure.getRootElement());
          for (Object child : childElements) {
            if (child instanceof ExternalLibrariesNode) {
              return (ExternalLibrariesNode)child;
            }
          }
          return null;
        }
      });
      if (node != null) {
        return new NodeFixture(node, treeStructure, myPane);
      }
      throw new AssertionError("Unable to find 'External Libraries' node");
    }

    public NodeFixture selectByPath(@NotNull final String... paths) {
      final AbstractTreeStructure treeStructure = getTreeStructure();

      final BasePsiNode node = GuiActionRunner.execute(new GuiQuery<BasePsiNode>() {
        @Nullable
        @Override
        protected BasePsiNode executeInEDT() throws Throwable {
          Object root = treeStructure.getRootElement();
          final List<Object> treePath = new ArrayList();
          treePath.add(root);

          for (String pathItem : paths) {
            Object[] childElements = treeStructure.getChildElements(root);
            Object newRoot = null;
            for (Object child : childElements) {
              if (child instanceof PsiDirectoryNode) {
                PsiDirectory dir = ((PsiDirectoryNode)child).getValue();
                if (dir != null && pathItem.equals(dir.getName())) {
                  newRoot = child;
                  treePath.add(newRoot);
                  break;
                }
              }
              if (child instanceof PsiFileNode) {
                PsiFile file = ((PsiFileNode)child).getValue();
                if (file != null && pathItem.equals(file.getName())) {
                  newRoot = child;
                  treePath.add(newRoot);
                  break;
                }
              }
              if (child instanceof ClassTreeNode) {
                ClassTreeNode ctn = (ClassTreeNode)child;
                if (ctn.getPsiClass().getContainingFile().getName().equals(pathItem)) {

                  newRoot = child;
                  treePath.add(newRoot);
                  break;
                }
              }
            }
            if (newRoot != null) {
              root = newRoot;
            }
            else {
              return null;
            }
          }
          if (root == treeStructure.getRootElement()) {
            return null;
          }

          VirtualFile vf2select = root instanceof ClassTreeNode
                                  ? ((ClassTreeNode)root).getPsiClass().getContainingFile().getVirtualFile()
                                  : ((BasePsiNode)root).getVirtualFile();

          myPane.expand(treePath.toArray(), true);
          myPane.select(root, vf2select, true);
          return (BasePsiNode)root;
        }
      });

      assertNotNull(node);

      pause(new Condition("Node to be selected") {
        @Override
        public boolean test() {
          return node.equals(GuiActionRunner.execute(new GuiQuery<Object>() {
            @Override
            protected Object executeInEDT() throws Throwable {
              DefaultMutableTreeNode selectedNode = myPane.getSelectedNode();
              if (selectedNode != null) {
                return selectedNode.getUserObject();
              }
              return null;
            }
          }));
        }
      }, SHORT_TIMEOUT);
      return (new NodeFixture(node, treeStructure, myPane));
    }

    public void getSelectedNodeLocation() {

    }
  }

  public class NodeFixture {
    @NotNull private final ProjectViewNode<?> myNode;
    @NotNull private final AbstractTreeStructure myTreeStructure;
    @NotNull private final AbstractProjectViewPane myPane;

    NodeFixture(@NotNull ProjectViewNode<?> node, @NotNull AbstractTreeStructure treeStructure, @NotNull AbstractProjectViewPane pane) {
      myNode = node;
      myTreeStructure = treeStructure;
      myPane = pane;
    }

    @NotNull
    public List<NodeFixture> getChildren() {
      final List<NodeFixture> children = new ArrayList<>();
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          for (Object child : myTreeStructure.getChildElements(myNode)) {
            if (child instanceof ProjectViewNode) {
              children.add(new NodeFixture((ProjectViewNode<?>)child, myTreeStructure, myPane));
            }
          }
        }
      });
      return children;
    }

    public Point getLocation() {
      return ReadAction.compute(() -> {
        myPane.getTree();
        final JTree tree = myPane.getTree();
        final DefaultMutableTreeNode dmtn = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), myNode);
        final TreePath path = TreeUtil.getPathFromRoot(dmtn);
        final Rectangle bounds = tree.getPathBounds(path);
        assertNotNull(bounds);
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
      });
    }


    public Point getLocationOnScreen() {
      final Point locationOnScreen = myPane.getComponentToFocus().getLocationOnScreen();
      final Point location = getLocation();
      return new Point(locationOnScreen.x + location.x, locationOnScreen.y + location.y);
    }

    public void click() {
      myRobot.click(getLocationOnScreen(), MouseButton.LEFT_BUTTON, 1);
    }

    public void doubleClick() {
      myRobot.click(getLocationOnScreen(), MouseButton.LEFT_BUTTON, 2);
    }

    public void rightClick() {
      invokeContextMenu();
    }

    public void invokeContextMenu() {
      myRobot.click(getLocationOnScreen(), MouseButton.RIGHT_BUTTON, 1);
    }

    public boolean isJdk() {
      if (myNode instanceof NamedLibraryElementNode) {
        NamedLibraryElement value = ((NamedLibraryElementNode)myNode).getValue();
        assertNotNull(value);
        LibraryOrSdkOrderEntry orderEntry = value.getOrderEntry();
        if (orderEntry instanceof JdkOrderEntry) {
          Sdk sdk = ((JdkOrderEntry)orderEntry).getJdk();
          return sdk.getSdkType() instanceof JavaSdk;
        }
      }
      return false;
    }

    @NotNull
    public NodeFixture requireDirectory(@NotNull String name) {
      assertThat(myNode).isInstanceOf(PsiDirectoryNode.class);
      VirtualFile file = myNode.getVirtualFile();
      assertNotNull(file);
      assertThat(file.getName()).isEqualTo(name);
      return this;
    }


    @Override
    public String toString() {
      return StringUtil.notNullize(myNode.getName());
    }
  }
}
