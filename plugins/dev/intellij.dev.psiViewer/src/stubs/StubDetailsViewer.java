// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.stubs;

import com.intellij.dev.psiViewer.DevPsiViewerBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.psi.stubs.ObjectStubBase;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.Invoker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

public class StubDetailsViewer {

  private static final boolean DEFAULT_SHOW_STUB_DETAILS = false;
  private static final String PROPERTY_NAME = "StubDetailsViewer.showPreviewDetails";

  private final @NotNull StubViewerPsiBasedTree myStubViewerTree;
  private final @NotNull Tree myStubDetailsTree;

  public StubDetailsViewer(@NotNull StubViewerPsiBasedTree tree) {
    myStubViewerTree = tree;
    myStubDetailsTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myStubDetailsTree.setRootVisible(false);
  }

  public AnAction addComponent(JBSplitter splitter) {
    updateStubDetailsTreeView(PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME, DEFAULT_SHOW_STUB_DETAILS), splitter);

    return new ToggleAction(DevPsiViewerBundle.message("action.show.stub.details.text"), null, AllIcons.Actions.PreviewDetails) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean(PROPERTY_NAME, DEFAULT_SHOW_STUB_DETAILS);
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(PROPERTY_NAME, state, DEFAULT_SHOW_STUB_DETAILS);
        updateStubDetailsTreeView(state, splitter);
        if (state) {
          valueChanged(myStubViewerTree.getSelectedStub());
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
  }

  private void updateStubDetailsTreeView(boolean isShown, JBSplitter splitter) {
    myStubDetailsTree.setEnabled(isShown);
    splitter.setSecondComponent(isShown ? ScrollPaneFactory.createScrollPane(myStubDetailsTree, true) : null);
  }

  public void valueChanged(@Nullable StubElement<?> stub) {
    if (myStubDetailsTree.isEnabled()) {
      Disposable treeModelDisposable = myStubViewerTree.myTreeModelDisposable;
      StructureTreeModel<?> model = new StructureTreeModel<>(
        new StubDetailsTreeStructure(stub), null, Invoker.forEventDispatchThread(treeModelDisposable), treeModelDisposable
      );
      myStubDetailsTree.setModel(model);
      myStubDetailsTree.expandRow(0);
      myStubDetailsTree.treeDidChange();
    }
  }

  private static class StubDetailsTreeStructure extends SimpleTreeStructure {

    private final Object myRoot;

    private StubDetailsTreeStructure(@Nullable StubElement<?> root) {
      myRoot = root != null ? new StubDetailsRootNode(root) : new Object();
    }

    @NotNull
    @Override
    public Object getRootElement() {
      return myRoot;
    }
  }

  private static class StubDetailsRootNode extends SimpleNode {

    private final @NotNull StubElement<?> myStub;

    private StubDetailsRootNode(@NotNull StubElement<?> stub) {
      super();
      myStub = stub;
    }

    @Override
    public SimpleNode @NotNull [] getChildren() {
      return ReflectionUtil.collectFields(myStub.getClass()).stream()
        .filter(f -> {
          if (Modifier.isStatic(f.getModifiers())) return false;
          Class<?> aClass = f.getDeclaringClass();
          return !StubBase.class.equals(aClass) && !ObjectStubBase.class.equals(aClass) && !AtomicReference.class.equals(aClass) &&
                 (f.canAccess(myStub) || f.trySetAccessible());
        })
        .map(f -> f.getName() + " = " + ReflectionUtil.getFieldValue(f, myStub))
        .sorted()
        .map(s -> new SimpleNode() {
          @Override
          public SimpleNode @NotNull [] getChildren() {
            return NO_CHILDREN;
          }

          @Override
          public String getName() {
            return s;
          }
        })
        .toArray(SimpleNode[]::new);
    }
  }
}
