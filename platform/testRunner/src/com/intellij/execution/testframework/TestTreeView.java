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
import com.intellij.pom.Navigatable;
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

public abstract class TestTreeView extends Tree implements UiCompatibleDataProvider, CopyProvider {
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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    TreePath[] paths = getSelectionPaths();
    TreePath selectionPath = getSelectionPath();
    sink.set(PlatformDataKeys.COPY_PROVIDER, this);
    if (selectionPath == null) return;

    sink.set(MODEL_DATA_KEY, myModel);

    AbstractTestProxy[] testProxies = Arrays.stream(Objects.requireNonNull(paths))
      .map(path -> getSelectedTest(path))
      .filter(Objects::nonNull)
      .toArray(AbstractTestProxy[]::new);
    sink.set(AbstractTestProxy.DATA_KEYS, testProxies);

    AbstractTestProxy testProxy = getSelectedTest(selectionPath);
    if (testProxy == null) return;

    sink.set(AbstractTestProxy.DATA_KEY, testProxy);
    if (testProxy instanceof Navigatable o) {
      sink.set(CommonDataKeys.NAVIGATABLE, o);
    }
    RunProfile configuration = myModel.getProperties().getConfiguration();
    if (configuration instanceof RunConfiguration o) {
      sink.set(RunConfiguration.DATA_KEY, o);
    }

    Project project = myModel.getProperties().getProject();
    TestFrameworkRunningModel model = myModel;
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      Location<?> location = testProxy.getLocation(project, model.getProperties().getScope());
      PsiElement psiElement = location != null ? location.getPsiElement() : null;
      return psiElement == null || !psiElement.isValid() ? null : psiElement;
    });
    sink.lazy(Location.DATA_KEY, () -> {
      return testProxy.getLocation(project, model.getProperties().getScope());
    });
    sink.lazy(Location.DATA_KEYS, () -> {
      return Arrays.stream(testProxies)
        .map(p -> p.getLocation(project, model.getProperties().getScope()))
        .filter(Objects::nonNull)
        .toArray(Location[]::new);
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      return Arrays.stream(testProxies)
        .map(p -> p.getLocation(project, model.getProperties().getScope()))
        .filter(Objects::nonNull).map(l -> l.getPsiElement())
        .toArray(PsiElement[]::new);
    });
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
    TreeSpeedSearch.installOn(this, canExpand, path -> {
      final AbstractTestProxy testProxy = getSelectedTest(path);
      if (testProxy == null) return null;
      return getPresentableName(testProxy);
    });
    TreeUtil.installActions(this);
    PopupHandler.installPopupMenu(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    HintUpdateSupply.installHintUpdateSupply(this, obj -> {
      Object userObject = TreeUtil.getUserObject(obj);
      Object element = userObject instanceof NodeDescriptor<?> o ? o.getElement() : null;
      if (!(element instanceof AbstractTestProxy testProxy)) {
        return null;
      }
      TestFrameworkRunningModel model = myModel;
      Project project = model.getProperties().getProject();
      Location<?> location = testProxy.getLocation(project, model.getProperties().getScope());
      PsiElement psiElement = location != null ? location.getPsiElement() : null;
      return psiElement == null || !psiElement.isValid() ? null : psiElement;
    });
  }

  protected String getPresentableName(AbstractTestProxy testProxy) {
    return testProxy.getName();
  }
}