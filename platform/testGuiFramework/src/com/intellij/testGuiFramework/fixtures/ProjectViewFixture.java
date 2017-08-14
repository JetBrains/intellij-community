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
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Timeout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ide.projectView.BaseProjectTreeBuilder.getBuilderFor;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static com.intellij.testGuiFramework.framework.GuiTestUtil.THIRTY_SEC_TIMEOUT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertNotNull;

public class ProjectViewFixture extends ToolWindowFixture {

  private static Logger LOG = Logger.getInstance("#com.intellij.testGuiFramework.fixtures.ProjectViewFixture");

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

  public void assertStructure(String expectedStructure) {
    Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
    AbstractTreeStructure treeStructure = selectProjectPane().getTreeStructure();
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectViewTestUtil.assertStructureEqual(treeStructure, expectedStructure, StringUtil.countNewLines(expectedStructure) + 1,
                                                 PlatformTestUtil.createComparator(printInfo), treeStructure.getRootElement(), printInfo);
      }
    });
  }

  public void assertAsyncStructure(String expectedStructure) {
    Ref<Throwable> lastThrowable = new Ref<>();
    Timeout timeout = SHORT_TIMEOUT;
    try {
      pause(new Condition("Waiting for project view update") {
        @Override
        public boolean test() {
          Queryable.PrintInfo printInfo = new Queryable.PrintInfo();
          AbstractTreeStructure treeStructure = selectProjectPane().getTreeStructure();
          return GuiActionRunner.execute(new GuiQuery<Boolean>() {
            @Override
            protected Boolean executeInEDT() throws Throwable {
              try {
                ProjectViewTestUtil.assertStructureEqual(treeStructure, expectedStructure, StringUtil.countNewLines(expectedStructure) + 1,
                                                         PlatformTestUtil.createComparator(printInfo), treeStructure.getRootElement(),
                                                         printInfo);
              }
              catch (AssertionError ae) {
                lastThrowable.set(ae);
                return false;
              }
              return true;
            }
          });
        }
      }, timeout);
    }
    catch (WaitTimedOutError error) {
      Throwable throwable = lastThrowable.get();
      if (throwable != null) {
        throw new AssertionError(
          "Failed on waiting project structure update for " + timeout.toString() + ", expected and actual structures are still different: ",
          throwable);
      }
      else {
        throw error;
      }
    }
  }

  /**
   * @param pathTo could be a separate vararg of String objects like ["project_name", "src", "Test.java"] or one String with a path
   *               separated by slash sign: ["project_name/src/Test.java"]
   * @return NodeFixture object for a pathTo; may be used for expanding, scrolling and clicking node
   */
  @NotNull
  public NodeFixture path(String... pathTo) {
    Ref<NodeFixture> nodeFixtureRef = new Ref<>();
    try {
      pause(new Condition("Waiting for a node by path: " + Arrays.toString(pathTo)) {
        @Override
        public boolean test() {
          try {
            nodeFixtureRef.set(getNodeFixtureByPath(pathTo));
            return true;
          }
          catch (ComponentLookupException e) {
            return false;
          }
        }
      }, THIRTY_SEC_TIMEOUT);
    }
    catch (WaitTimedOutError timedOutError) {
      AbstractTreeStructure treeStructure = selectProjectPane().getTreeStructure();
      StringBuilder projectViewStructure = PlatformTestUtil.print(treeStructure, treeStructure.getRootElement(), 10, null, 100, ' ', null);
      LOG.error("Unable to find path: " +
                Arrays.toString(pathTo) +
                " for current project structure.\nActual project structure" +
                projectViewStructure, timedOutError);
    }
    return nodeFixtureRef.get();
  }

  @NotNull
  private NodeFixture getNodeFixtureByPath(String[] pathTo) {
    if (pathTo.length == 1) {
      if (pathTo[0].contains("/")) {
        String[] newPath = pathTo[0].split("/");
        return selectProjectPane().getNode(newPath);
      }
    }
    return selectProjectPane().getNode(pathTo);
  }

  public boolean containsPath(String... pathTo) {
    return getNodeFixtureByPath(pathTo) != null;
  }

  public class PaneFixture {
    @NotNull private final AbstractProjectViewPane myPane;
    @NotNull private final AbstractTreeBuilder myBuilder;

    PaneFixture(@NotNull AbstractProjectViewPane pane) {
      myPane = pane;
      myBuilder = getBuilderFor(myPane.getTree());
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


    public NodeFixture expandByPath(@NotNull final String... paths) {
      NodeFixture nodeFixture = getNode(paths);
      nodeFixture.expand();
      return nodeFixture;
    }

    @NotNull
    private NodeFixture getNode(@NotNull String[] path) {
      AbstractTreeStructure treeStructure = getTreeStructure();
      ProjectViewNode projectViewNode = GuiActionRunner.execute(new GuiQuery<ProjectViewNode>() {
        @Nullable
        @Override
        protected ProjectViewNode executeInEDT() throws Throwable {
          Object root = treeStructure.getRootElement();
          ExtendedTreeFixture treeFixture = new ExtendedTreeFixture(myRobot, myPane.getTree());
          Tree tree = (Tree)myPane.getTree();
          final ArrayList<Object> treePath = new ArrayList();
          treePath.add(root);

          for (String pathItem : path) {
            Object[] childElements = treeStructure.getChildElements(root); //check root
            Object newRoot = null;
            for (Object child : childElements) {
              treePath.add(child);
              String nodeText = getNodeText(child);
              if (nodeText == null) {
                LOG.error("Unable to get text of project view node for pathItem: " + pathItem);
                throw new AssertionError("Unable to get text of project view node for pathItem: " + pathItem);
              }
              if (nodeText.equals(pathItem)) {
                newRoot = child;
                break;
              }
              else {
                treePath.remove(treePath.size() - 1);
              }
            }
            if (newRoot != null) {
              root = newRoot;
            }
            else {
              throw new ComponentLookupException("Unable to find node with next path: " + Arrays.toString(path));
            }
          }

          return (ProjectViewNode)root;
        }
      });
      if (projectViewNode == null) throw new ComponentLookupException("Unable to find node with next path: " + Arrays.toString(path));
      return new NodeFixture(projectViewNode, treeStructure, myPane);
    }
  }

  @Nullable
  private static String getNodeText(Object node) {
    assert (node instanceof PresentableNodeDescriptor);
    PresentableNodeDescriptor descriptor = (PresentableNodeDescriptor)node;
    descriptor.update();
    return descriptor.getPresentation().getPresentableText();
  }

  public class NodeFixture {
    @NotNull private final ProjectViewNode<?> myNode;
    @NotNull private final AbstractTreeStructure myTreeStructure;
    @NotNull private final AbstractProjectViewPane myPane;
    @NotNull private final AbstractTreeBuilder myBuilder;
    @NotNull private final Object[] myPath;

    NodeFixture(@NotNull ProjectViewNode<?> node, @NotNull AbstractTreeStructure treeStructure, @NotNull AbstractProjectViewPane pane) {
      myNode = node;
      myTreeStructure = treeStructure;
      myPane = pane;
      myPath = createPath(treeStructure, node);
      myBuilder = getBuilderFor(myPane.getTree());
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
      Ref<DefaultMutableTreeNode> mutableTreeNodeRef = new Ref<>();
      myPane.getTree();
      final JTree tree = myPane.getTree();

      pause(new Condition("Waiting until default tree node for node: " + myNode.getTitle() + " will be loaded") {
        @Override
        public boolean test() {
          final DefaultMutableTreeNode treeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), myNode);
          if (treeNode == null) {
            return false;
          }
          else {
            mutableTreeNodeRef.set(treeNode);
            return true;
          }
        }
      }, GuiTestUtil.THIRTY_SEC_TIMEOUT);
      assertNotNull(mutableTreeNodeRef.get());

      Ref<Rectangle> boundsRef = new Ref<>();
      pause(new Condition("Waiting until bounds of tree node: " + myNode.getTitle() + " will be not null") {
        @Override
        public boolean test() {
          return ReadAction.compute(() -> {
            final TreePath path = TreeUtil.getPathFromRoot(mutableTreeNodeRef.get());
            final Rectangle bounds = tree.getPathBounds(path);
            if (bounds != null) boundsRef.set(bounds);
            return (bounds != null);
          });
        }
      }, GuiTestUtil.THIRTY_SEC_TIMEOUT);

      Rectangle bounds = boundsRef.get();
      return new Point(bounds.x + bounds.height / 2, bounds.y + bounds.height / 2);
    }


    public Point getLocationOnScreen() {
      final Point locationOnScreen = myPane.getComponentToFocus().getLocationOnScreen();
      final Point location = getLocation();
      return new Point(locationOnScreen.x + location.x, locationOnScreen.y + location.y);
    }

    public void click() {
      expand().scrollTo();
      myRobot.click(getLocationOnScreen(), MouseButton.LEFT_BUTTON, 1);
    }

    public void doubleClick() {
      expand().scrollTo();
      myRobot.click(getLocationOnScreen(), MouseButton.LEFT_BUTTON, 2);
    }

    public void rightClick() {
      invokeContextMenu();
    }

    public void invokeContextMenu() {
      expand().scrollTo();
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

    private Object[] createPath(AbstractTreeStructure ats, Object node) {
      ArrayList<Object> buildPath = new ArrayList<>();
      Object root = ats.getRootElement();
      Object curr = node;
      while (curr != root) {
        buildPath.add(0, curr);
        curr = ats.getParentElement(curr);
      }
      buildPath.add(0, curr);
      return buildPath.toArray();
    }


    @Override
    public String toString() {
      return StringUtil.notNullize(myNode.getName());
    }

    @NotNull
    public NodeFixture expand() {
      CountDownLatch cdl = new CountDownLatch(1);
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          myBuilder.expand(myNode, () -> cdl.countDown());
        }
      });
      try {
        cdl.await();
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      return this;
    }

    @NotNull
    public NodeFixture scrollTo() {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          myBuilder.scrollTo(myNode);
        }
      });
      return this;
    }
  }
}
