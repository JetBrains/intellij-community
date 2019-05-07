// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNode;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNodeBase;
import com.intellij.ide.util.newProjectWizard.FrameworksTree;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Vector;

/**
 * @author Sergey Karashevich
 */
public class FrameworksTreeFixture {

  Robot myRobot;
  FrameworksTree myFrameworksTree;
  DefaultTreeModel myAdaptiveTree;

  public FrameworksTreeFixture(Robot robot, FrameworksTree frameworksTree, DefaultTreeModel adaptiveTree) {
    myRobot = robot;
    myFrameworksTree = frameworksTree;
    myAdaptiveTree = adaptiveTree;
  }

  @NotNull
  public static FrameworksTreeFixture find(@NotNull Robot robot) {
    final FrameworksTree frameworksTree = robot.finder().findByType(FrameworksTree.class);
    final DefaultTreeModel adaptiveTree = createAdaptiveTree(frameworksTree);
    return new FrameworksTreeFixture(robot, frameworksTree, adaptiveTree);
  }

  @NotNull
  public FrameworksTreeFixture selectFramework(String frameworkName){
    myAdaptiveTree.getRoot();

    final Object parent = myAdaptiveTree.getRoot();
    for (int i = 0; i < myAdaptiveTree.getChildCount(parent); i++) {
      FrameworkSupportElement frameworkSupportElement =
        (FrameworkSupportElement)((ComponentNode)myAdaptiveTree.getChild(parent, i)).getComponent();
      if (frameworkSupportElement.getText().equals(frameworkName)) {
        //scroll tree to path
        final TreePath treePath = myFrameworksTree.getPathForRow(i);
        GuiActionRunner.execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            myFrameworksTree.scrollPathToVisible(treePath);
          }
        });

        final JCheckBox checkbox = frameworkSupportElement.getCheckbox();
        myRobot.click(checkbox);
        return this;
      }
    }
    return this;
  }

  private static DefaultTreeModel createAdaptiveTree(Component component) {
    return new DefaultTreeModel(new ComponentNode(component));
  }

  public static class ComponentNode extends DefaultMutableTreeNode {
    private final Component myComponent;
    String myText;

    public ComponentNode(@NotNull Component component) {
      super(component);
      myComponent = component;
      children = prepareChildren(myComponent);
    }

    Component getComponent() {
      return myComponent;
    }

    @Override
    public String toString() {
      return myText != null ? myText : myComponent.getClass().getName();
    }

    public void setText(String value) {
      myText = value;
    }

    public String getText() {
      return myText;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ComponentNode && ((ComponentNode)obj).getComponent() == getComponent();
    }

    @SuppressWarnings("UseOfObsoleteCollectionType")
    private static Vector prepareChildren(Component component) {
      if (!(component instanceof FrameworksTree)) return null;
      Vector<ComponentNode> result = new Vector<>();
      final Tree tree = (Tree)component;
      final int rowCount = tree.getRowCount();
      for (int i = 0; i < rowCount; i++) {
        final TreePath treePath = tree.getPathForRow(i);
        final Rectangle rowBounds = tree.getPathBounds(treePath);
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getPathComponent(1);
        if (component instanceof ProjectViewTree) {
          final String cmpText = treePath.getLastPathComponent().toString();
          ProjectViewElement projectViewElement = new ProjectViewElement(tree, node.getClass(), cmpText);
          projectViewElement.setBounds(rowBounds);
          projectViewElement.setVisible(true);
          result.add(new ComponentNode(projectViewElement));
        }
        else if (component instanceof FrameworksTree) {
          FrameworksTree fTree = (FrameworksTree)component;
          final FrameworkSupportNodeBase fsnb = (FrameworkSupportNodeBase) treePath.getLastPathComponent();
          FrameworkSupportElement frameworkSupportElement =
            new FrameworkSupportElement(tree, node.getClass(), fsnb.getUserObject().getPresentableName(), fsnb instanceof FrameworkSupportNode, fTree);
          frameworkSupportElement.setBounds(rowBounds);
          frameworkSupportElement.setVisible(true);
          result.add(new ComponentNode(frameworkSupportElement));
        }
      }

      return result;
    }
  }

  public static class ProjectViewElement extends JLabel {

    Tree myTree;
    Class myOriginalClass;
    String myText;

    public ProjectViewElement(Tree tree, Class originalClass, String text) {
      super(text);
      myText = text;
      myTree = tree;
      myOriginalClass = originalClass;
    }

    @Override
    public Point getLocationOnScreen() {
      final Point parentLocationOnscreen = getParent().getLocationOnScreen();
      final Point result = new Point(parentLocationOnscreen.x + getBounds().x, parentLocationOnscreen.y + getBounds().y);
      return result;
    }

    @Override
    public Container getParent() {
      return myTree;
    }
  }

  public static class FrameworkSupportElement extends JLabel {
    Tree myTree;
    Class myOriginalClass;
    String myText;
    boolean myHasCheckbox;
    FrameworksTree myFrameworksTree;

    public FrameworkSupportElement(Tree tree, Class originalClass, String text, boolean hasCheckbox, FrameworksTree frameworksTree) {
      super(text);
      myText = text;
      myTree = tree;
      myOriginalClass = originalClass;
      myHasCheckbox = hasCheckbox;
      myFrameworksTree = frameworksTree;
    }

    @Nullable
    public JCheckBox getCheckbox() {
      if (!myHasCheckbox) return null;
      KCheckBox checkBox = new KCheckBox(this);
      final Rectangle checkboxBounds =
        ((CheckboxTreeBase.CheckboxTreeCellRendererBase)myFrameworksTree.getCellRenderer()).myCheckbox.getBounds();
      checkBox.setBounds(checkboxBounds);
      checkBox.setVisible(true);
      return checkBox;
    }


    @Override
    public Point getLocationOnScreen() {
      final Point parentLocationOnscreen = getParent().getLocationOnScreen();
      final Point result = new Point(parentLocationOnscreen.x + getBounds().x, parentLocationOnscreen.y + getBounds().y);
      return result;
    }

    @Override
    public Container getParent() {
      return myTree;
    }
  }

  public static class KCheckBox extends JCheckBox {

    Component parent;

    public KCheckBox(Component parent) {
      super();
      this.parent = parent;
    }

    @Override
    public Point getLocationOnScreen() {
      return parent.getLocationOnScreen();
    }
  }
}
