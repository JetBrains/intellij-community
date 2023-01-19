// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
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
import java.util.Arrays;
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
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public Object getData(@NotNull final String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
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
    TreePath selectionPath = getSelectionPath();
    if (selectionPath == null) return null;
    AbstractTestProxy testProxy = getSelectedTest(selectionPath);
    if (testProxy == null) return null;

    if (AbstractTestProxy.DATA_KEY.is(dataId) || CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return testProxy;
    }

    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      TestFrameworkRunningModel model = myModel;
      return (DataProvider)slowId -> getSlowData(slowId, testProxy, model);
    }
    if (RunConfiguration.DATA_KEY.is(dataId)) {
      RunProfile configuration = myModel.getProperties().getConfiguration();
      if (configuration instanceof RunConfiguration) {
        return configuration;
      }
    }

    return null;
  }

  @Nullable
  private Object getSlowData(@NotNull String dataId,
                             @NotNull AbstractTestProxy testProxy,
                             @NotNull TestFrameworkRunningModel model) {
    Project project = model.getProperties().getProject();

    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      Location<?> location = testProxy.getLocation(project, model.getProperties().getScope());
      PsiElement psiElement = location != null ? location.getPsiElement() : null;
      return psiElement == null || !psiElement.isValid() ? null : psiElement;
    }
    else if (Location.DATA_KEY.is(dataId)) {
      return testProxy.getLocation(project, model.getProperties().getScope());
    }
    else if (Location.DATA_KEYS.is(dataId)) {
      AbstractTestProxy[] proxies = AbstractTestProxy.DATA_KEYS.getData(this);
      return proxies == null ? null : Arrays.stream(proxies)
        .map(p -> p.getLocation(project, model.getProperties().getScope()))
        .filter(Objects::nonNull)
        .toArray(Location[]::new);
    }
    else if (PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      AbstractTestProxy[] proxies = AbstractTestProxy.DATA_KEYS.getData(this);
      return proxies == null ? null : Arrays.stream(proxies)
        .map(p -> p.getLocation(project, model.getProperties().getScope()))
        .filter(Objects::nonNull).map(l -> l.getPsiElement())
        .toArray(PsiElement[]::new);
    }

    return null;
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
    boolean canExpand = Registry.is("tests.view.node.expanding.search");
    new TreeSpeedSearch(this, canExpand, path -> {
      final AbstractTestProxy testProxy = getSelectedTest(path);
      if (testProxy == null) return null;
      return getPresentableName(testProxy);
    });
    TreeUtil.installActions(this);
    PopupHandler.installPopupMenu(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    HintUpdateSupply.installHintUpdateSupply(this, obj -> {
      Object userObject = TreeUtil.getUserObject(obj);
      Object element = userObject instanceof NodeDescriptor? ((NodeDescriptor<?>)userObject).getElement() : null;
      if (element instanceof AbstractTestProxy) {
        return (PsiElement)getSlowData(CommonDataKeys.PSI_ELEMENT.getName(), (AbstractTestProxy)element, myModel);
      }
      return null;
    });
  }

  protected String getPresentableName(AbstractTestProxy testProxy) {
    return testProxy.getName();
  }
}