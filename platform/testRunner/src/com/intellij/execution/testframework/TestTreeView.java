// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.ui.render.RenderingHelper.SHRINK_LONG_RENDERER;

public abstract class TestTreeView extends Tree implements DataProvider, CopyProvider {
  public static final DataKey<TestFrameworkRunningModel> MODEL_DATA_KEY = DataKey.create("testFrameworkModel.dataId");

  private TestFrameworkRunningModel myModel;

  public TestTreeView() {
    setLargeModel(true);
  }

  protected abstract TreeCellRenderer getRenderer(TestConsoleProperties properties);

  public abstract AbstractTestProxy getSelectedTest(@NotNull TreePath selectionPath);

  protected TestFrameworkRunningModel getTestFrameworkRunningModel() {
    return myModel;
  }

  @Nullable
  public AbstractTestProxy getSelectedTest() {
    TreePath[] paths = getSelectionPaths();
    if (paths != null && paths.length > 1) return null;
    final TreePath selectionPath = getSelectionPath();
    return selectionPath != null ? getSelectedTest(selectionPath) : null;
  }

  public void attachToModel(final TestFrameworkRunningModel model) {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode(model.getRoot())));
    getSelectionModel().setSelectionMode(model.getProperties().getSelectionMode());
    myModel = model;
    Disposer.register(myModel, myModel.getRoot());
    Disposer.register(myModel, new Disposable() {
      @Override
      public void dispose() {
        setModel(null);
        myModel = null;
      }
    });
    installHandlers();
    setCellRenderer(getRenderer(myModel.getProperties()));
    putClientProperty(SHRINK_LONG_RENDERER, true);
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);
  }

  @Override
  public Object getData(@NotNull final String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }

    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths != null && paths.length > 1) {
        final List<PsiElement> els = new ArrayList<>(paths.length);
        for (TreePath path : paths) {
          if (isPathSelected(path.getParentPath())) continue;
          AbstractTestProxy test = getSelectedTest(path);
          if (test != null) {
            final PsiElement psiElement = (PsiElement)TestsUIUtil.getData(test, CommonDataKeys.PSI_ELEMENT.getName(), myModel);
            if (psiElement != null) {
              els.add(psiElement);
            }
          }
        }
        return els.isEmpty() ? null : els.toArray(PsiElement.EMPTY_ARRAY);
      }
    }

    if (Location.DATA_KEYS.is(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths != null && paths.length > 1) {
        final List<Location<?>> locations = new ArrayList<>(paths.length);
        for (TreePath path : paths) {
          if (isPathSelected(path.getParentPath())) continue;
          AbstractTestProxy test = getSelectedTest(path);
          if (test != null) {
            final Location<?> location = (Location<?>)TestsUIUtil.getData(test, Location.DATA_KEY.getName(), myModel);
            if (location != null) {
              locations.add(location);
            }
          }
        }
        return locations.isEmpty() ? null : locations.toArray(new Location[0]);
      }
    }

    if (AbstractTestProxy.DATA_KEYS.is(dataId)) {
      TreePath[] paths = getSelectionPaths();
      if (paths != null) {
        return Arrays.stream(paths)
          .map(path -> getSelectedTest(path))
          .filter(Objects::nonNull)
          .toArray(AbstractTestProxy[]::new);
      }
    }

    if (MODEL_DATA_KEY.is(dataId)) {
      return myModel;
    }

    final TreePath selectionPath = getSelectionPath();
    if (selectionPath == null) return null;
    final AbstractTestProxy testProxy = getSelectedTest(selectionPath);
    if (testProxy == null) return null;
    try {
      return TestsUIUtil.getData(testProxy, dataId, myModel);
    }
    catch (IndexNotReadyException ignore) {
      return null;
    }
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    final String fqn;
    if (element != null) {
      fqn = CopyReferenceAction.elementToFqn(element);
    }
    else {
      AbstractTestProxy selectedTest = getSelectedTest();
      fqn = selectedTest != null ? selectedTest.getLocationUrl() : null;
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(fqn));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    AbstractTestProxy test = getSelectedTest();
    return test != null && test.getLocationUrl() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  protected void installHandlers() {
    EditSourceOnDoubleClickHandler.install(this);
    EditSourceOnEnterKeyHandler.install(this);
    new TreeSpeedSearch(this, path -> {
      final AbstractTestProxy testProxy = getSelectedTest(path);
      if (testProxy == null) return null;
      return getPresentableName(testProxy);
    });
    TreeUtil.installActions(this);
    PopupHandler.installPopupHandler(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    HintUpdateSupply.installHintUpdateSupply(this, obj -> {
      if (obj instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)obj).getUserObject();
        if (userObject instanceof NodeDescriptor) {
          Object element = ((NodeDescriptor)userObject).getElement();
          if (element instanceof AbstractTestProxy) {
            return (PsiElement)TestsUIUtil.getData((AbstractTestProxy)element, CommonDataKeys.PSI_ELEMENT.getName(), myModel);
          }
        }
      }
      return null;
    });
  }

  protected String getPresentableName(AbstractTestProxy testProxy) {
    return testProxy.getName();
  }
}