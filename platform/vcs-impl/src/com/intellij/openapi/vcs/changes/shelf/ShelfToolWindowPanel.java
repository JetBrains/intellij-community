// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.diff.impl.DiffEditorViewer;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.dnd.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.DiffPreview;
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager;
import com.intellij.openapi.vcs.changes.PreviewDiffSplitterComponent;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.SHELF;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerKt.isCommitToolWindowShown;

final class ShelfToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
  private static final @NotNull RegistryValue isOpenEditorDiffPreviewWithSingleClick =
    Registry.get("show.diff.preview.as.editor.tab.with.single.click");

  private final Project myProject;
  private final ShelveChangesManager myShelveChangesManager;
  private final VcsConfiguration myVcsConfiguration;

  private final @NotNull JScrollPane myTreeScrollPane;
  private final ShelvedChangesViewManager.ShelfTree myTree;

  private final @NotNull ShelveEditorDiffPreview myEditorDiffPreview;
  private @Nullable ShelveSplitterDiffPreview mySplitterDiffPreview;

  private boolean myDisposed;

  ShelfToolWindowPanel(@NotNull Project project) {
    super(true);
    myProject = project;
    myShelveChangesManager = ShelveChangesManager.getInstance(myProject);
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);

    myTree = new ShelvedChangesViewManager.ShelfTree(myProject);
    myTree.setEditable(true);
    myTree.setDragEnabled(!ApplicationManager.getApplication().isHeadlessEnvironment());
    myTree.setCellEditor(new ShelveRenameTreeCellEditor());

    final AnAction showDiffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON);
    showDiffAction.registerCustomShortcutSet(showDiffAction.getShortcutSet(), myTree);
    final EditSourceAction editSourceAction = new EditSourceAction();
    editSourceAction.registerCustomShortcutSet(editSourceAction.getShortcutSet(), myTree);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAll((ActionGroup)ActionManager.getInstance().getAction(ShelvedChangesViewManager.SHELVED_CHANGES_TOOLBAR));
    actionGroup.add(Separator.getInstance());
    actionGroup.add(new MyToggleDetailsAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ShelvedChanges", actionGroup, false);
    toolbar.setTargetComponent(myTree);
    myTreeScrollPane = ScrollPaneFactory.createScrollPane(myTree, true);

    setContent(myTreeScrollPane);
    setToolbar(toolbar.getComponent());
    updatePanelLayout();

    myEditorDiffPreview = new ShelveEditorDiffPreview();
    Disposer.register(this, myEditorDiffPreview);

    setSplitterDiffPreview();
    EditorTabDiffPreviewManager.getInstance(project).subscribeToPreviewVisibilityChange(this, this::setSplitterDiffPreview);

    myProject.getMessageBus().connect(this).subscribe(ChangesViewContentManagerListener.TOPIC, () -> updatePanelLayout());

    PopupHandler.installPopupMenu(myTree, "ShelvedChangesPopupMenu", ShelvedChangesViewManager.SHELF_CONTEXT_MENU);
    new MyDnDSupport(myProject, myTree, myTreeScrollPane).install(this);
  }

  @Override
  public void dispose() {
    myDisposed = true;

    if (mySplitterDiffPreview != null) Disposer.dispose(mySplitterDiffPreview);
    mySplitterDiffPreview = null;

    myTree.shutdown();
  }

  private void updatePanelLayout() {
    setVertical(isCommitToolWindowShown(myProject));
  }

  private void setSplitterDiffPreview() {
    boolean hasSplitterPreview = !isCommitToolWindowShown(myProject);

    //noinspection DoubleNegation
    boolean needUpdatePreview = hasSplitterPreview != (mySplitterDiffPreview != null);
    if (!needUpdatePreview) return;

    if (hasSplitterPreview) {
      mySplitterDiffPreview = new ShelveSplitterDiffPreview();
      DiffPreview.setPreviewVisible(mySplitterDiffPreview, myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN);
    }
    else {
      Disposer.dispose(mySplitterDiffPreview);
      mySplitterDiffPreview = null;
    }
  }

  private class ShelveEditorDiffPreview extends TreeHandlerEditorDiffPreview {
    private ShelveEditorDiffPreview() {
      super(myTree, myTreeScrollPane, ShelvedPreviewProcessor.ShelveTreeDiffPreviewHandler.INSTANCE);
    }

    @NotNull
    @Override
    protected DiffEditorViewer createViewer() {
      return new ShelvedPreviewProcessor(myProject, myTree, true);
    }

    @Override
    public void returnFocusToTree() {
      ToolWindow toolWindow = getToolWindowFor(myProject, SHELF);
      if (toolWindow != null) toolWindow.activate(null);
    }

    @Override
    public void updateDiffAction(@NotNull AnActionEvent event) {
      DiffShelvedChangesActionProvider.updateAvailability(event);
    }

    @Nullable
    @Override
    public String getEditorTabName(@Nullable ChangeViewDiffRequestProcessor.Wrapper wrapper) {
      return wrapper != null
             ? VcsBundle.message("shelve.editor.diff.preview.title", wrapper.getPresentableName())
             : VcsBundle.message("shelved.version.name");
    }

    @Override
    protected boolean isOpenPreviewWithSingleClickEnabled() {
      return isOpenEditorDiffPreviewWithSingleClick.asBoolean();
    }

    @Override
    protected boolean isOpenPreviewWithSingleClick() {
      if (mySplitterDiffPreview != null && myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN) return false;
      return super.isOpenPreviewWithSingleClick();
    }
  }

  private class ShelveSplitterDiffPreview implements DiffPreview, Disposable {
    private final ShelvedPreviewProcessor myProcessor;
    private final PreviewDiffSplitterComponent mySplitterComponent;

    private ShelveSplitterDiffPreview() {
      myProcessor = new ShelvedPreviewProcessor(myProject, myTree, false);
      mySplitterComponent = new PreviewDiffSplitterComponent(myProcessor, ShelvedChangesViewManager.SHELVE_PREVIEW_SPLITTER_PROPORTION);

      mySplitterComponent.setFirstComponent(myTreeScrollPane);
      ShelfToolWindowPanel.this.setContent(mySplitterComponent);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myProcessor);

      if (!ShelfToolWindowPanel.this.myDisposed) {
        ShelfToolWindowPanel.this.setContent(myTreeScrollPane);
      }
    }

    @Override
    public boolean openPreview(boolean requestFocus) {
      return mySplitterComponent.openPreview(requestFocus);
    }

    @Override
    public void closePreview() {
      mySplitterComponent.closePreview();
    }
  }

  private static class MyDnDSupport implements DnDDropHandler, DnDTargetChecker {
    private final @NotNull Project myProject;
    private final @NotNull ChangesTree myTree;
    private final @NotNull JScrollPane myTreeScrollPane;

    private MyDnDSupport(@NotNull Project project,
                         @NotNull ChangesTree tree,
                         @NotNull JScrollPane treeScrollPane) {
      myProject = project;
      myTree = tree;
      myTreeScrollPane = treeScrollPane;
    }

    public void install(@NotNull Disposable disposable) {
      DnDSupport.createBuilder(myTree)
        .setTargetChecker(this)
        .setDropHandler(this)
        .setImageProvider(this::createDraggedImage)
        .setBeanProvider(this::createDragStartBean)
        .setDisposableParent(disposable)
        .install();
    }

    @Override
    public void drop(DnDEvent aEvent) {
      ShelvedChangesViewManager.handleDropEvent(myProject, aEvent);
    }

    @Override
    public boolean update(DnDEvent aEvent) {
      aEvent.hideHighlighter();
      aEvent.setDropPossible(false, "");

      boolean canHandle = ShelvedChangesViewManager.canHandleDropEvent(myProject, aEvent);
      if (!canHandle) return true;

      // highlight top of the tree
      Rectangle tableCellRect = new Rectangle(0, 0, JBUI.scale(300), JBUI.scale(12));
      aEvent.setHighlighting(new RelativeRectangle(myTreeScrollPane, tableCellRect), DnDEvent.DropTargetHighlightingType.RECTANGLE);
      aEvent.setDropPossible(true);

      return false;
    }

    private @Nullable DnDDragStartBean createDragStartBean(@NotNull DnDActionInfo info) {
      if (info.isMove()) {
        DataContext dc = DataManager.getInstance().getDataContext(myTree);
        return new DnDDragStartBean(new ShelvedChangeListDragBean(ShelvedChangesViewManager.getShelveChanges(dc),
                                                                  ShelvedChangesViewManager.getBinaryShelveChanges(dc),
                                                                  ShelvedChangesViewManager.getShelvedLists(dc)));
      }
      return null;
    }

    private @NotNull DnDImage createDraggedImage(@NotNull DnDActionInfo info) {
      String imageText = VcsBundle.message("unshelve.changes.action");
      return ChangesTreeDnDSupport.createDragImage(myTree, imageText);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW, myEditorDiffPreview);
  }

  private class MyToggleDetailsAction extends ShowDiffPreviewAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(mySplitterDiffPreview != null || isOpenEditorDiffPreviewWithSingleClick.asBoolean());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DiffPreview previewSplitter = ObjectUtils.chooseNotNull(mySplitterDiffPreview, myEditorDiffPreview);
      DiffPreview.setPreviewVisible(previewSplitter, state);
      myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN = state;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.SHELVE_DETAILS_PREVIEW_SHOWN;
    }
  }

  private class ShelveRenameTreeCellEditor extends DefaultTreeCellEditor implements CellEditorListener {
    ShelveRenameTreeCellEditor() {
      super(myTree, null);
      addCellEditorListener(this);
    }

    @Override
    public boolean isCellEditable(EventObject event) {
      return !(event instanceof MouseEvent) && super.isCellEditable(event);
    }

    @Override
    public void editingStopped(ChangeEvent e) {
      TreeNode node = (TreeNode)myTree.getLastSelectedPathComponent();
      if (node instanceof ShelvedListNode changeListNode &&
          e.getSource() instanceof TreeCellEditor treeCellEditor) {
        String editorValue = treeCellEditor.getCellEditorValue().toString();
        ShelvedChangeList shelvedChangeList = changeListNode.getList();
        myShelveChangesManager.renameChangeList(shelvedChangeList, editorValue);
      }
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
    }
  }
}
