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

import com.intellij.execution.Location;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class TestTreeView extends Tree implements DataProvider, CopyProvider {
  public static final DataKey<TestFrameworkRunningModel> MODEL_DATA_KEY = DataKey.create("testFrameworkModel.dataId");

  private TestFrameworkRunningModel myModel;

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
        return els.isEmpty() ? null : els.toArray(new PsiElement[els.size()]);
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
        return locations.isEmpty() ? null : locations.toArray(new Location[locations.size()]);
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
      fqn = selectedTest instanceof TestProxyRoot ? ((TestProxyRoot)selectedTest).getRootLocation() 
                                                  : selectedTest != null ? selectedTest.getLocationUrl() : null;
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(fqn));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    AbstractTestProxy test = getSelectedTest();
    if (test instanceof TestProxyRoot) {
      return ((TestProxyRoot)test).getRootLocation() != null;
    }
    return test != null && test.getLocationUrl() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
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

  public boolean isExpandableHandlerVisibleForCurrentRow(int row) {
    final ExpandableItemsHandler<Integer> handler = getExpandableItemsHandler();
    final Collection<Integer> items = handler.getExpandedItems();
    return items.size() == 1 && row == items.iterator().next();
  }
  
  @Override
  public void paint(Graphics g) {
    super.paint(g);
    final TestFrameworkRunningModel model = myModel;
    if (model == null) return;
    final TestConsoleProperties properties = model.getProperties();
    if (TestConsoleProperties.SHOW_INLINE_STATISTICS.value(properties)) {
      Rectangle visibleRect = getVisibleRect();
      Rectangle clip = g.getClipBounds();
      final int visibleRowCount = TreeUtil.getVisibleRowCountForFixedRowHeight(this);
      final int firstRow = getClosestRowForLocation(0, visibleRect.y);
      for (int row = firstRow; row < Math.min(firstRow + visibleRowCount + 1, getRowCount()); row++) {
        if (isExpandableHandlerVisibleForCurrentRow(row)) {
          continue;
        }
        Object node = getPathForRow(row).getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)node).getUserObject();
          if (data instanceof BaseTestProxyNodeDescriptor) {
            final AbstractTestProxy testProxy = ((BaseTestProxyNodeDescriptor)data).getElement();
            final String durationString = testProxy.getDurationString(properties);
            if (durationString != null) {
              Rectangle rowBounds = getRowBounds(row);
              rowBounds.x = 0;
              rowBounds.width = Integer.MAX_VALUE;

              if (rowBounds.intersects(clip)) {
                final Rectangle fullRowRect = new Rectangle(visibleRect.x, rowBounds.y, visibleRect.width, rowBounds.height);
                final boolean rowSelected = isRowSelected(row);
                final boolean hasTreeFocus = hasFocus();
                paintRowData(this, durationString, fullRowRect, (Graphics2D)g, rowSelected, hasTreeFocus);
              }
            }
          }
        }
      }
    }
  }

  private static void paintRowData(Tree tree, String duration, Rectangle bounds, Graphics2D g, boolean isSelected, boolean hasFocus) {
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    g.setFont(tree.getFont().deriveFont(Font.PLAIN, UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
    final FontMetrics metrics = tree.getFontMetrics(g.getFont());
    int totalWidth = metrics.stringWidth(duration) + 2;
    int x = bounds.x + bounds.width - totalWidth;
    g.setColor(isSelected ? UIUtil.getTreeSelectionBackground(hasFocus) : UIUtil.getTreeBackground());
    final int leftOffset = 5;
    g.fillRect(x - leftOffset, bounds.y, totalWidth + leftOffset, bounds.height);
    g.translate(0, bounds.y - 1);
    if (isSelected) {
      if (!hasFocus && UIUtil.isUnderAquaBasedLookAndFeel()) {
        g.setColor(UIUtil.getTreeForeground());
      }
      else {
        g.setColor(UIUtil.getTreeSelectionForeground());
      }
    }
    else {
      g.setColor(new JBColor(0x808080, 0x808080));
    }
    g.drawString(duration, x, SimpleColoredComponent.getTextBaseLine(tree.getFontMetrics(tree.getFont()), bounds.height) + 1);
    g.translate(0, -bounds.y + 1);
    config.restore();
  }
}