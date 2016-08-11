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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 12/1/2015
 */
public class TaskExecutionView implements ConsoleView, DataProvider {

  private static final int TREE_COLUMN_MIN_WIDTH = 300;
  private static final int TIME_COLUMN_MIN_WIDTH = 140;
  private final Project myProject;
  private final Map<String, ExecutionNode> nodeMap = ContainerUtil.newHashMap();
  private final JScrollPane myPane;
  private final TreeTable myTreeTable;
  private final SimpleTreeBuilder myBuilder;
  private final NodeProgressAnimator myProgressAnimator;
  private final ExecutionNode myRoot;
  private String myWorkingDir;

  public TaskExecutionView(Project project) {
    myProject = project;
    final ColumnInfo[] COLUMNS = new ColumnInfo[]{
      new TreeColumnInfo("name"),
      new ColumnInfo("time elapsed") {
        @Nullable
        @Override
        public Object valueOf(Object o) {
          if (o instanceof DefaultMutableTreeNode) {
            final Object userObject = ((DefaultMutableTreeNode)o).getUserObject();
            if (userObject instanceof ExecutionNode) {
              return ((ExecutionNode)userObject).getDuration();
            }
          }
          return null;
        }
      }
      ,
      new ColumnInfo("") {
        @Nullable
        @Override
        public Object valueOf(Object o) {
          return "";
        }
      }
    };
    myRoot = new ExecutionNode(project, myWorkingDir);
    myRoot.setInfo(new ExecutionInfo(null, "Run build", myWorkingDir));
    final ListTreeTableModelOnColumns model = new ListTreeTableModelOnColumns(new DefaultMutableTreeNode(myRoot), COLUMNS);

    myTreeTable = new TaskExecutionTreeTable(model);
    myTreeTable.setRowHeight(myTreeTable.getRowHeight() + 2);
    final TreeCellRenderer treeCellRenderer = myTreeTable.getTree().getCellRenderer();

    myTreeTable.getTree().setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        final Component rendererComponent =
          treeCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (rendererComponent instanceof SimpleColoredComponent) {
          final Color bg = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
          final Color fg = selected ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
          if (selected) {
            for (SimpleColoredComponent.ColoredIterator it = ((SimpleColoredComponent)rendererComponent).iterator(); it.hasNext(); ) {
              it.next();
              int offset = it.getOffset();
              int endOffset = it.getEndOffset();
              SimpleTextAttributes currentAttributes = it.getTextAttributes();
              SimpleTextAttributes newAttributes =
                new SimpleTextAttributes(bg, fg, currentAttributes.getWaveColor(), currentAttributes.getStyle());
              it.split(endOffset - offset, newAttributes);
            }
          }

          SpeedSearchUtil.applySpeedSearchHighlighting(myTreeTable, (SimpleColoredComponent)rendererComponent, true, selected);
        }
        return rendererComponent;
      }
    });

    new TreeTableSpeedSearch(myTreeTable).setComparator(new SpeedSearchComparator(false));
    myTreeTable.setTableHeader(null);

    final TableColumn treeColumn = myTreeTable.getColumnModel().getColumn(0);
    treeColumn.setMinWidth(TREE_COLUMN_MIN_WIDTH);
    final TableColumn timeColumn = myTreeTable.getColumnModel().getColumn(1);
    timeColumn.setMinWidth(TIME_COLUMN_MIN_WIDTH);

    TreeTableTree tree = myTreeTable.getTree();
    final SimpleTreeStructure treeStructure = new SimpleTreeStructure.Impl(myRoot);

    myBuilder = new SimpleTreeBuilder(tree, model, treeStructure, null);
    Disposer.register(this, myBuilder);
    myBuilder.expand(treeStructure.getRootElement(), null);

    myBuilder.initRoot();
    myBuilder.expand(myRoot, null);
    myProgressAnimator = new NodeProgressAnimator(myBuilder);
    myProgressAnimator.setCurrentNode(myRoot);
    myBuilder.queueUpdateFrom(myRoot, false, true);

    myPane = ScrollPaneFactory.createScrollPane(myTreeTable,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    DataManager.registerDataProvider(myTreeTable, this);
  }

  public void onStatusChange(ExternalSystemTaskExecutionEvent event) {
    final ExternalSystemProgressEvent progressEvent = event.getProgressEvent();
    final String parentEventId = progressEvent.getParentEventId();
    if (progressEvent instanceof ExternalSystemStartEvent) {
      final ExecutionInfo executionInfo = new ExecutionInfo(progressEvent.getEventId(), progressEvent.getDescriptor(), myWorkingDir);
      executionInfo.setStartTime(progressEvent.getEventTime());
      final ExecutionNode currentNode = parentEventId == null ? myRoot : new ExecutionNode(myProject, myWorkingDir);
      if (parentEventId != null) {
        final ExecutionNode parentNode = nodeMap.get(parentEventId);
        if (parentNode != null) {
          parentNode.add(currentNode);
        }
      }
      currentNode.setInfo(executionInfo);
      nodeMap.put(progressEvent.getEventId(), currentNode);

      myProgressAnimator.setCurrentNode(currentNode);
      myBuilder.queueUpdateFrom(currentNode, false, true);
    }
    else if (progressEvent instanceof ExternalSystemFinishEvent) {
      final ExecutionInfo executionInfo;
      final ExecutionNode node = nodeMap.get(progressEvent.getEventId());
      executionInfo = node.getInfo();
      executionInfo.setDescriptor(progressEvent.getDescriptor());
      executionInfo.setEndTime(progressEvent.getEventTime());
      final OperationResult operationResult = ((ExternalSystemFinishEvent)progressEvent).getOperationResult();
      if (operationResult instanceof FailureResult) {
        executionInfo.setFailed(true);
      }
      else if (operationResult instanceof SkippedResult) {
        executionInfo.setSkipped(true);
      }
      else if (operationResult instanceof SuccessResult) {
        executionInfo.setUpToDate(((SuccessResult)operationResult).isUpToDate());
      }
      if (parentEventId == null) {
        myProgressAnimator.stopMovie();
      }

      myBuilder.queueUpdateFrom(node, false, false);
    }
    else if (progressEvent instanceof ExternalSystemProgressEventUnsupported) {
      final ExecutionInfo executionInfo =
        new ExecutionInfo(null, "Build/task progress visualization available in Gradle version >= 2.5", myWorkingDir);
      executionInfo.setSkipped(true);
      myRoot.setInfo(executionInfo);
      myProgressAnimator.stopMovie();
    }
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    final TreeTableTree tree = myTreeTable.getTree();

    if (Location.DATA_KEYS.is(dataId)) {
      TreePath[] paths = tree.getSelectionModel().getSelectionPaths();
      if (paths != null && paths.length > 1) {
        final List<Location<?>> locations = new ArrayList<>(paths.length);
        for (TreePath path : paths) {
          if (tree.isPathSelected(path.getParentPath())) continue;
          ExecutionInfo executionInfo = getSelectedExecution(path);
          if (executionInfo != null) {
            final Location<?> location = (Location<?>)GradleRunnerUtil.getData(myProject, Location.DATA_KEY.getName(), executionInfo);
            if (location != null) {
              locations.add(location);
            }
          }
        }
        return locations.isEmpty() ? null : locations.toArray(new Location[locations.size()]);
      }
    }

    if (Location.DATA_KEY.is(dataId)) {
      TreePath[] paths = tree.getSelectionModel().getSelectionPaths();
      if (paths != null && paths.length > 1) {
        final List<ExecutionInfo> executionInfos = ContainerUtil.newArrayListWithCapacity(paths.length);
        for (TreePath path : paths) {
          if (tree.isPathSelected(path.getParentPath())) continue;
          ExecutionInfo executionInfo = getSelectedExecution(path);
          ContainerUtil.addIfNotNull(executionInfos, executionInfo);
        }
        return executionInfos.isEmpty()
               ? null
               : GradleRunnerUtil.getTaskLocation(myProject, executionInfos.toArray(new ExecutionInfo[executionInfos.size()]));
      }
    }

    final TreePath selectionPath = tree.getSelectionPath();
    if (selectionPath == null) return null;
    ExecutionInfo executionInfo = getSelectedExecution(selectionPath);
    if (executionInfo == null) return null;
    return GradleRunnerUtil.getData(myProject, dataId, executionInfo);
  }

  @Override
  public void dispose() {
    DataManager.removeDataProvider(myTreeTable);
  }

  public void onFailure(Exception e) {
    myRoot.getInfo().setFailed(true);
    myProgressAnimator.stopMovie();
  }

  void setWorkingDir(String workingDir) {
    myWorkingDir = workingDir;
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
  }

  @Override
  public void clear() {

  }

  @Override
  public void scrollTo(int offset) {

  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {

  }

  @Override
  public void setOutputPaused(boolean value) {

  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(Runnable runnable) {

  }

  @Override
  public void setHelpId(String helpId) {

  }

  @Override
  public void addMessageFilter(Filter filter) {

  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() {

  }

  @Override
  public JComponent getComponent() {
    return myPane;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTreeTable;
  }

  private static ExecutionInfo getSelectedExecution(@NotNull final TreePath selectionPath) {
    final Object lastComponent = selectionPath.getLastPathComponent();
    assert lastComponent != null;
    return getExecutionInfo(lastComponent);
  }

  @Nullable
  private static ExecutionInfo getExecutionInfo(final Object treeNode) {
    final Object userObj = ((DefaultMutableTreeNode)treeNode).getUserObject();
    if (userObj instanceof ExecutionNode) {
      return ((ExecutionNode)userObj).getInfo();
    }
    return null;
  }
}
