// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view;

import com.intellij.CommonBundle;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageLogger;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.actions.ExternalReportImportAction;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBTreeTable;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoverageView extends BorderLayoutPanel implements DataProvider, Disposable {
  @NonNls private static final String ACTION_DRILL_DOWN = "DrillDown";
  @NonNls public static final String HELP_ID = "reference.toolWindows.Coverage";
  public static final Icon FILTER_ICON = AllIcons.General.Filter;

  private final CoverageTableModel myModel;
  private final JBTreeTable myTable;
  private final Project myProject;
  private final CoverageViewManager.StateBean myStateBean;
  private final CoverageSuitesBundle mySuitesBundle;
  private final CoverageViewExtension myViewExtension;
  private final CoverageViewTreeStructure myTreeStructure;
  private boolean myHasVCSFilter = false;
  private boolean myHasFullyCoveredFilter = false;


  public CoverageView(Project project, CoverageSuitesBundle bundle, CoverageViewManager.StateBean stateBean) {
    myProject = project;
    myStateBean = stateBean;
    mySuitesBundle = bundle;
    myViewExtension = mySuitesBundle.getCoverageEngine().createCoverageViewExtension(myProject, mySuitesBundle, myStateBean);
    myTreeStructure = new CoverageViewTreeStructure(project, mySuitesBundle, stateBean);

    myModel = new CoverageTableModel(mySuitesBundle, stateBean, project, myTreeStructure);
    Disposer.register(this, myModel);
    myTable = new JBTreeTable(myModel);
    TreeUtil.expand(myTable.getTree(), 2);
    myTable.getTree().setCellRenderer(new NodeRenderer() {
      @Override
      protected @NotNull SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation,
                                                                      Color color,
                                                                      @NotNull Object node) {
        if (mySelected) color = null;
        return super.getSimpleTextAttributes(presentation, color, node);
      }
    });
    myTable.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setBackground(UIUtil.getTableBackground(isSelected, myTable.hasFocus()));
        return component;
      }
    });

    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(CoverageViewManager.TOOLWINDOW_ID);
    final boolean isHorizontalView = toolWindow != null && toolWindow.getAnchor().isHorizontal();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("CoverageView", createToolbarActions(), !isHorizontalView);
    actionToolbar.setTargetComponent(myTable);
    final JComponent toolbarComponent = actionToolbar.getComponent();
    if (isHorizontalView) {
      addToLeft(toolbarComponent);
    }
    else {
      addToTop(toolbarComponent);
    }
    setUpShowRootNode(actionToolbar);
    CoverageLogger.logViewOpen(project, myStateBean.isShowOnlyModified(), myHasVCSFilter, myStateBean.isHideFullyCovered(), myHasFullyCoveredFilter);

    final CoverageRowSorter rowSorter = new CoverageRowSorter(myTable, myModel);
    myTable.setRowSorter(rowSorter);
    if (stateBean.mySortingColumn < 0 || stateBean.mySortingColumn >= myModel.getColumnCount()) {
      stateBean.myAscendingOrder = true;
      stateBean.mySortingColumn = 0;
    }
    final RowSorter.SortKey sortKey = new RowSorter.SortKey(stateBean.mySortingColumn, stateBean.myAscendingOrder ? SortOrder.ASCENDING : SortOrder.DESCENDING);
    rowSorter.setSortKeys(Collections.singletonList(sortKey));
    addToCenter(myTable);

    attachFileStatusListener();

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        enterSelected(false);
        return true;
      }
    }.installOn(myTable.getTree());

    final TreeSpeedSearch speedSearch = TreeSpeedSearch.installOn(myTable.getTree(), false, path -> path.getLastPathComponent().toString());
    speedSearch.setCanExpand(true);
    speedSearch.setClearSearchOnNavigateNoMatch(true);
    PopupHandler.installPopupMenu(myTable, createPopupGroup(), "CoverageViewPopup");

    myTable.getTree().registerKeyboardAction(e -> resetView(), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_FOCUSED);
    myTable.getTree().getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_DRILL_DOWN);
    myTable.getTree().getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK), ACTION_DRILL_DOWN);
    myTable.getTree().getActionMap().put(ACTION_DRILL_DOWN, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        enterSelected(true);
      }
    });
  }

  private void resetIfAllFiltered(AbstractTreeNode<?> root, ActionToolbar actionToolbar) {
    if (myViewExtension.hasVCSFilteredNodes() && myStateBean.isShowOnlyModified() && myStateBean.isDefaultFilters()) {
      if (!myViewExtension.hasChildren(root)) {
        myStateBean.setShowOnlyModified(false);
        resetView();
      }
      else {
        final String message = CoverageBundle.message("coverage.filter.gotit", myViewExtension.getElementsName());
        final GotItTooltip gotIt = new GotItTooltip("coverage.view.elements.filter", message, this);
        if (gotIt.canShow()) {
          final JComponent filterAction = findToolbarActionButtonWithIcon(actionToolbar, FILTER_ICON);
          if (filterAction != null) {
            gotIt.show(filterAction, GotItTooltip.BOTTOM_MIDDLE);
          }
        }
      }
    }
  }

  private void setUpShowRootNode(ActionToolbar actionToolbar) {
    final var showFull = new Ref<>(false);
    myModel.addTreeModelListener(new TreeModelListener() {
      private volatile boolean called = false;

      @Override
      public void treeNodesChanged(TreeModelEvent e) {
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        onModelUpdate(e);
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        onModelUpdate(e);
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        onModelUpdate(e);
      }

      private void onModelUpdate(TreeModelEvent e) {
        final Object root = myModel.getRoot();
        if (e.getTreePath().getLastPathComponent() == root) {
          setUpEmptyText();
          final int childCount = myModel.getChildCount(root);
          final boolean showRoot = childCount > 1 || childCount == 1 && showFull.get();
          if (showRoot && !myStateBean.isShowOnlyModified() && !myStateBean.isHideFullyCovered()) {
            showFull.set(true);
          }
          if (showRoot != myTable.getTree().isRootVisible()) {
            myTable.getTree().setRootVisible(showRoot);
          }
          if (!called) {
            var nodeRoot = myModel.getCoverageNode(root);
            if (nodeRoot != null) {
              called = true;
              setWidth(nodeRoot);
              resetIfAllFiltered(nodeRoot, actionToolbar);
            }
          }
        }
      }
    });
  }

  private void attachFileStatusListener() {
    final FileStatusListener fileStatusListener = new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        myTable.repaint();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
        myTable.repaint();
      }
    };
    FileStatusManager.getInstance(myProject).addFileStatusListener(fileStatusListener, this);
  }

  private void setUpEmptyText() {
    boolean hasVcsFiltered = myViewExtension.hasVCSFilteredNodes();
    boolean hasFullyCovered = myViewExtension.hasFullyCoveredNodes();
    myTable.getTree().getEmptyText().clear();
    final StatusText emptyText = myTable.getTable().getEmptyText();
    emptyText.setText(CoverageBundle.message("coverage.view.no.coverage.results"));
    final RunConfigurationBase<?> configuration = mySuitesBundle.getRunConfiguration();
    if (configuration != null) {
      emptyText.appendLine(CoverageBundle.message("coverage.view.edit.run.configuration.0") + " ");
      emptyText.appendText(CoverageBundle.message("coverage.view.edit.run.configuration.1"), SimpleTextAttributes.LINK_ATTRIBUTES, e -> {
        final RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(myProject).findSettings(configuration);
        if (configurationSettings != null) {
          RunDialog.editConfiguration(myProject, configurationSettings,
                                      ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", configuration.getName()));
        }
        else {
          Messages.showErrorDialog(myProject, CoverageBundle.message("coverage.view.configuration.was.not.found", configuration.getName()),
                                   CommonBundle.getErrorTitle());
        }
      });
      emptyText.appendText(" " + CoverageBundle.message("coverage.view.edit.run.configuration.2"));
    }
    if (hasVcsFiltered && myStateBean.isShowOnlyModified()) {
      emptyText.appendLine(CoverageBundle.message("coverage.show.unmodified.elements", myViewExtension.getElementsName()), SimpleTextAttributes.LINK_ATTRIBUTES, e -> {
        myStateBean.setShowOnlyModified(false);
        resetView();
      });
    }
    if (hasFullyCovered && myStateBean.isHideFullyCovered()) {
      emptyText.appendLine(CoverageBundle.message("coverage.show.fully.covered.elements", myViewExtension.getElementsName()), SimpleTextAttributes.LINK_ATTRIBUTES, e -> {
        myStateBean.setHideFullyCovered(false);
        resetView();
      });
    }
  }

  @Override
  public void dispose() {
    if (!myProject.isDisposed()) {
      CoverageDataManager.getInstance(myProject).closeSuitesBundle(mySuitesBundle);
    }
  }

  public void saveSize() {
    final int columns = myTable.getTable().getColumnCount();
    final List<Integer> widths = new ArrayList<>();
    final TableColumnModel columnModel = myTable.getTable().getColumnModel();
    for (int i = 0; i < columns; i++) {
      widths.add(columnModel.getColumn(i).getWidth());
    }
    // tree width comes last
    widths.add(myTable.getWidth() - myTable.getTable().getWidth());
    myStateBean.myColumnSize = widths;

    final RowSorter<? extends TableModel> sorter = myTable.getTable().getRowSorter();
    RowSorter.SortKey sortKey = null;
    if (sorter != null) {
      final List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
      if (keys != null && !keys.isEmpty()) {
        sortKey = keys.get(0);
      }
    }
    if (sortKey != null && sortKey.getSortOrder() != SortOrder.UNSORTED) {
      myStateBean.mySortingColumn = sortKey.getColumn();
      myStateBean.myAscendingOrder = sortKey.getSortOrder() == SortOrder.ASCENDING;
    }
  }

  private void setWidth(AbstractTreeNode<?> root) {
    final int columns = myTable.getTable().getColumnCount();
    final TableColumnModel columnModel = myTable.getTable().getColumnModel();
    int tableWidth = 0;
    final int nameWidth;
    if (myStateBean.myColumnSize != null && myStateBean.myColumnSize.size() == columns + 1) {
      for (int column = 0; column < columns; column++) {
        final int width = myStateBean.myColumnSize.get(column);
        columnModel.getColumn(column).setPreferredWidth(width);
        tableWidth += width;
      }
      nameWidth = myStateBean.myColumnSize.get(columns);
    }
    else {
      for (int column = 0; column < columns; column++) {
        final int width = Math.max(getStringWidth(myModel.getColumnName(column)), getColumnWidth(column, root));
        columnModel.getColumn(column).setPreferredWidth(width);
        tableWidth += width;
      }
      nameWidth = Math.max(getStringWidth(myModel.getColumnName(0)), JBUIScale.scale(150));
    }
    myTable.setColumnProportion(((float)tableWidth) / (nameWidth + tableWidth) / columns);
  }

  private int getColumnWidth(int column, AbstractTreeNode<?> root) {
    final String preferredString = myViewExtension.getPercentage(column, root);
    if (preferredString == null) return JBUIScale.scale(60);
    return getStringWidth(preferredString);
  }

  private int getStringWidth(@NotNull String preferredString) {
    final FontMetrics fontMetrics = getFontMetrics(getFont());
    return fontMetrics.stringWidth(preferredString);
  }

  private static ActionGroup createPopupGroup() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    return actionGroup;
  }

  private ActionGroup createToolbarActions() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (myViewExtension.supportFlattenPackages()) {
      actionGroup.add(new FlattenPackagesAction());
    }

    installAutoScrollToSource(actionGroup);
    installAutoScrollFromSource(actionGroup);

    actionGroup.add(ActionManager.getInstance().getAction("GenerateCoverageReport"));
    actionGroup.add(new ExternalReportImportAction());

    List<AnAction> extraActions = myViewExtension.createExtraToolbarActions();
    extraActions.forEach(actionGroup::add);


    boolean hasFilters = false;
    final DefaultActionGroup filtersActionGroup = new DefaultActionGroup();
    if (ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) {
      filtersActionGroup.add(new ShowOnlyModifiedAction());
      hasFilters = true;
      myHasVCSFilter = true;
    }
    else {
      myStateBean.setShowOnlyModified(false);
    }
    if (myViewExtension.supportFlattenPackages()) {
      filtersActionGroup.add(new HideFullyCoveredAction());
      hasFilters = true;
      myHasFullyCoveredFilter = true;
    }
    else {
      myStateBean.setHideFullyCovered(false);
    }
    if (hasFilters) {
      filtersActionGroup.setPopup(true);
      filtersActionGroup.getTemplatePresentation().setIcon(FILTER_ICON);
      filtersActionGroup.getTemplatePresentation().setText(CoverageBundle.messagePointer("coverage.view.filters.group"));
      actionGroup.add(filtersActionGroup);
    }

    return actionGroup;
  }

  private void installAutoScrollFromSource(DefaultActionGroup actionGroup) {
    final MyAutoScrollFromSourceHandler handler = new MyAutoScrollFromSourceHandler();
    handler.install();
    actionGroup.add(handler.createToggleAction());
  }

  private void installAutoScrollToSource(DefaultActionGroup actionGroup) {
    AutoScrollToSourceHandler autoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myStateBean.myAutoScrollToSource;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myStateBean.myAutoScrollToSource = state;
      }
    };
    autoScrollToSourceHandler.install(myTable.getTree());
    actionGroup.add(autoScrollToSourceHandler.createToggleAction());
  }

  private void enterSelected(final boolean expand) {
    final TreePath path = getSelectedPath();
    final AbstractTreeNode<?> element = getLast(path);
    if (element == null) return;
    if (myModel.isLeaf(path.getLastPathComponent())) {
      if (element.canNavigate()) {
        element.navigate(true);
      }
      return;
    }
    if (!expand) return;
    if (!myTable.getTree().isExpanded(path)) {
      myTable.getTree().expandPath(path);
    }
    else {
      myTable.getTree().collapsePath(path);
    }
  }

  private TreePath getSelectedPath() {
    return myTable.getTree().getSelectionPath();
  }

  private AbstractTreeNode<?> getLast(@Nullable TreePath path) {
    if (path == null) return null;
    return myModel.getCoverageNode(path.getLastPathComponent());
  }

  private AbstractTreeNode<?> getSelectedValue() {
    return getLast(getSelectedPath());
  }

  public boolean canSelect(VirtualFile file) {
    return myViewExtension.canSelectInCoverageView(file);
  }

  public void select(VirtualFile file) {
    select(myViewExtension.getElementToSelect(file));
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return getSelectedValue();
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return null;
  }

  private void resetView() {
    myTreeStructure.reset();
    ApplicationManager.getApplication().executeOnPooledThread(() -> myModel.reset(true));
  }

  private final class FlattenPackagesAction extends ToggleAction {

    private FlattenPackagesAction() {
      super(IdeBundle.messagePointer("action.flatten.packages"), CoverageBundle.messagePointer("coverage.flatten.packages"), AllIcons.ObjectBrowser.FlattenPackages);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myStateBean.isFlattenPackages();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myStateBean.setFlattenPackages(state);
      resetView();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private final class HideFullyCoveredAction extends ToggleAction {

    private HideFullyCoveredAction() {
      super(CoverageBundle.messagePointer("coverage.hide.fully.covered.elements", myViewExtension.getElementsCapitalisedName()));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myStateBean.isHideFullyCovered();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myStateBean.setHideFullyCovered(state);
      resetView();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private final class ShowOnlyModifiedAction extends ToggleAction {

    private ShowOnlyModifiedAction() {
      super(CoverageBundle.messagePointer("coverage.show.only.modified.elements", myViewExtension.getElementsCapitalisedName()));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myStateBean.isShowOnlyModified();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myStateBean.setShowOnlyModified(state);
      resetView();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private void select(Object object) {
    ReadAction.nonBlocking(() -> {
        final PsiElement element = myViewExtension.getElementToSelect(object);
        final VirtualFile file = myViewExtension.getVirtualFile(object);
        return getNode(element, file);
      })
      .finishOnUiThread(ModalityState.nonModal(), (node) -> myModel.makeVisible(node, this::selectPath))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void selectPath(TreePath path) {
    if (path == null) return;
    myTable.getTree().addSelectionPath(path);
    ScrollingUtil.ensureSelectionExists(myTable.getTable());
  }

  private AbstractTreeNode<?> getNode(PsiElement element, VirtualFile file) {
    AbstractTreeNode<?> node = (AbstractTreeNode<?>)myTreeStructure.getRootElement();
    down:
    while (true) {
      if (Comparing.equal(node.getValue(), element)) break;
      for (Object child : myTreeStructure.getChildElements(node)) {
        final CoverageListNode childNode = (CoverageListNode)child;
        if (childNode.contains(file)) {
          node = childNode;
          continue down;
        }
      }
      break;
    }
    return node;
  }

  private class MyAutoScrollFromSourceHandler extends AutoScrollFromSourceHandler {
    MyAutoScrollFromSourceHandler() {
      super(CoverageView.this.myProject, CoverageView.this, CoverageView.this);
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return myStateBean.myAutoScrollFromSource;
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      myStateBean.myAutoScrollFromSource = state;
    }

    @Override
    protected void selectElementFromEditor(@NotNull FileEditor editor) {
      if (myProject.isDisposed() || !CoverageView.this.isShowing()) return;
      if (myStateBean.myAutoScrollFromSource) {
        VirtualFile file = editor.getFile();
        if (file != null && canSelect(file)) {
          PsiElement e = null;
          if (editor instanceof TextEditor) {
            int offset = ((TextEditor)editor).getEditor().getCaretModel().getOffset();
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
            if (psiFile != null) {
              e = psiFile.findElementAt(offset);
            }
          }
          select(e != null ? e : file);
        }
      }
    }
  }

  private static JComponent findToolbarActionButtonWithIcon(ActionToolbar toolbar, Icon icon) {
    return UIUtil.uiTraverser(toolbar.getComponent())
      .filter(ActionButton.class)
      .filter(button -> button.getIcon() == icon)
      .first();
  }
}