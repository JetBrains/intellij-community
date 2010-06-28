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

/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.TreeUI;
import javax.swing.tree.*;

public abstract class TestTreeView extends Tree implements DataProvider {
  private TestFrameworkRunningModel myModel;

  protected abstract TreeCellRenderer getRenderer(TestConsoleProperties properties);

  public abstract AbstractTestProxy getSelectedTest(@NotNull TreePath selectionPath);

  @Nullable
  public AbstractTestProxy getSelectedTest() {
    final TreePath selectionPath = getSelectionPath();
    return selectionPath != null ? getSelectedTest(selectionPath) : null;
  }

  public void attachToModel(final TestFrameworkRunningModel model) {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode(model.getRoot())));
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myModel = model;
    Disposer.register(myModel, new Disposable() {
      public void dispose() {
        setModel(null);
        myModel = null;
      }
    });
    installHandlers();
    setCellRenderer(getRenderer(myModel.getProperties()));
  }

  public void setUI(final TreeUI ui) {
    super.setUI(ui);
    final int fontHeight = getFontMetrics(getFont()).getHeight();
    final int iconHeight = PoolOfTestIcons.PASSED_ICON.getIconHeight();
    setRowHeight(Math.max(fontHeight, iconHeight) + 2);
    setLargeModel(true);
  }

  public Object getData(final String dataId) {
    final TreePath selectionPath = getSelectionPath();
    if (selectionPath == null) return null;
    final AbstractTestProxy testProxy = getSelectedTest(selectionPath);
    if (testProxy == null) return null;
    return TestsUIUtil.getData(testProxy, dataId, myModel);
  }

  protected void installHandlers() {
    EditSourceOnDoubleClickHandler.install(this);
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      public String convert(final TreePath path) {
        final AbstractTestProxy testProxy = getSelectedTest(path);
        if (testProxy == null) return null;
        return testProxy.getName();
      }
    });
    TreeUtil.installActions(this);
    PopupHandler.installPopupHandler(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
  }
}