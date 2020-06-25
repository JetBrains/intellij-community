// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.tabActions.CloseTab;
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentWithMnemonics;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.*;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutSettingsManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public final class EditorTabbedContainer implements CloseAction.CloseTarget {
  private final EditorWindow myWindow;
  private final Project myProject;
  private final JBTabsEx myTabs;

  @NonNls
  public static final String HELP_ID = "ideaInterface.editor";

  private final TabInfo.DragOutDelegate myDragOutDelegate = new MyDragOutDelegate();

  EditorTabbedContainer(@NotNull EditorWindow window, @NotNull Project project, @NotNull Disposable parentDisposable) {
    myWindow = window;
    myProject = project;
    myTabs = new EditorTabs(project, parentDisposable, window);
    myTabs.getComponent().setFocusable(false);
    myTabs.getComponent().setTransferHandler(new MyTransferHandler());
    myTabs
      .setDataProvider(new MyDataProvider())
      .setPopupGroup(
        () -> (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP), ActionPlaces.EDITOR_TAB_POPUP, false)
      .addTabMouseListener(new TabMouseListener()).getPresentation()
      .setTabDraggingEnabled(true)
      .setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL).setTabLabelActionsAutoHide(false)
      .setActiveTabFillIn(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()).setPaintFocus(false).getJBTabs()
      .addListener(new TabsListener() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
          FileEditor oldEditor = oldSelection != null ? editorManager.getSelectedEditor((VirtualFile)oldSelection.getObject()) : null;
          if (oldEditor != null) {
            oldEditor.deselectNotify();
          }

          VirtualFile newFile = (VirtualFile)newSelection.getObject();
          FileEditor newEditor = editorManager.getSelectedEditor(newFile);
          if (newEditor != null) {
            newEditor.selectNotify();
          }

          if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
            VfsUtil.markDirtyAndRefresh(true, false, false, newFile);
          }
        }
      })
      .setSelectionChangeHandler((info, requestFocus, doChangeSelection) -> {
        ActionCallback result = new ActionCallback();
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();
          result.notify(doChangeSelection.run());
        }, "EditorChange", null);
        return result;
      });
    myTabs.getPresentation().setRequestFocusOnLastFocusedComponent(true);
    myTabs.getComponent().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myTabs.findInfo(e) != null || isFloating()) return;
        if (!e.isPopupTrigger() && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
          doHideAll(e);
        }
      }
    });

    setTabPlacement(UISettings.getInstance().getEditorTabPlacement());

    if (JBTabsImpl.NEW_TABS) {
      TabsLayoutInfo tabsLayoutInfo = TabsLayoutSettingsManager.getInstance().getSelectedTabsLayoutInfo();
      myTabs.updateTabsLayout(tabsLayoutInfo);
    }
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  @NotNull
  public ActionCallback setSelectedIndex(int indexToSelect) {
    return setSelectedIndex(indexToSelect, true);
  }

  @NotNull
  public ActionCallback setSelectedIndex(int indexToSelect, boolean focusEditor) {
    if (indexToSelect >= myTabs.getTabCount()) return ActionCallback.REJECTED;
    return myTabs.select(myTabs.getTabAt(indexToSelect), focusEditor);
  }

  @NotNull
  public static DockableEditor createDockableEditor(Project project, Image image, VirtualFile file, Presentation presentation, EditorWindow window) {
    return new DockableEditor(project, image, file, presentation, window.getSize(), window.isFilePinned(file));
  }

  @NotNull
  public JComponent getComponent() {
    return myTabs.getComponent();
  }

  public ActionCallback removeTabAt(int componentIndex, int indexToSelect, boolean transferFocus) {
    TabInfo toSelect = indexToSelect >= 0 && indexToSelect < myTabs.getTabCount() ? myTabs.getTabAt(indexToSelect) : null;
    TabInfo info = myTabs.getTabAt(componentIndex);
    // removing hidden tab happens on end of drag-out, we've already selected the correct tab for this case in dragOutStarted
    if (info.isHidden() || !myProject.isOpen()) {
      toSelect = null;
    }
    ActionCallback callback = myTabs.removeTab(info, toSelect, transferFocus);
    return myProject.isOpen() ? callback : ActionCallback.DONE;
  }

  public ActionCallback removeTabAt(int componentIndex, int indexToSelect) {
    return removeTabAt(componentIndex, indexToSelect, true);
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  void setForegroundAt(int index, @NotNull Color color) {
    myTabs.getTabAt(index).setDefaultForeground(color);
  }

  void setWaveColor(int index, @Nullable Color color) {
    TabInfo tab = myTabs.getTabAt(index);
    tab.setDefaultStyle(color == null ? SimpleTextAttributes.STYLE_PLAIN : SimpleTextAttributes.STYLE_WAVED);
    tab.setDefaultWaveColor(color);
  }

  void setIconAt(int index, Icon icon) {
    myTabs.getTabAt(index).setIcon(UISettings.getInstance().getShowFileIconInTabs() ? icon : null);
  }

  Icon getIconAt(int index) {
    return myTabs.getTabAt(index).getIcon();
  }

  void setTitleAt(int index, @NotNull String text) {
    myTabs.getTabAt(index).setText(text);
  }

  void setToolTipTextAt(int index, String text) {
    myTabs.getTabAt(index).setTooltipText(text);
  }

  void setBackgroundColorAt(int index, @Nullable Color color) {
    myTabs.getTabAt(index).setTabColor(color);
  }

  void setTabLayoutPolicy(int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void setTabPlacement(int tabPlacement) {
    switch (tabPlacement) {
      case SwingConstants.TOP:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.top);
        break;
      case SwingConstants.BOTTOM:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.bottom);
        break;
      case SwingConstants.LEFT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.left);
        break;
      case SwingConstants.RIGHT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.right);
        break;
      case UISettings.TABS_NONE:
        myTabs.getPresentation().setHideTabs(true);
        break;
      default:
        throw new IllegalArgumentException("Unknown tab placement code=" + tabPlacement);
    }
  }

  void updateTabsLayout(@NotNull TabsLayoutInfo newTabsLayoutInfo) {
    myTabs.updateTabsLayout(newTabsLayoutInfo);
  }

  /**
   * @param ignorePopup if {@code false} and context menu is shown currently for some tab,
   *                    component for which menu is invoked will be returned
   */
  @Nullable
  public Object getSelectedComponent(boolean ignorePopup) {
    TabInfo info = ignorePopup ? myTabs.getSelectedInfo() : myTabs.getTargetInfo();
    return info != null ? info.getComponent() : null;
  }

  public void insertTab(@NotNull VirtualFile file,
                        Icon icon,
                        @NotNull JComponent component,
                        @Nullable String tooltip,
                        int indexToInsert,
                        @NotNull Disposable parentDisposable) {
    TabInfo tab = myTabs.findInfo(file);
    if (tab != null) {
      return;
    }

    tab = new TabInfo(component)
      .setText(EditorTabPresentationUtil.getEditorTabTitle(myProject, file, myWindow))
      .setTabColor(EditorTabPresentationUtil.getEditorTabBackgroundColor(myProject, file, myWindow))
      .setIcon(UISettings.getInstance().getShowFileIconInTabs() ? icon : null)
      .setTooltipText(tooltip)
      .setObject(file)
      .setDragOutDelegate(myDragOutDelegate);
    tab.setTestableUi(new MyQueryable(tab));

    CloseTab closeTab = new CloseTab(component, file, myProject, myWindow, parentDisposable);
    DataContext dataContext = DataManager.getInstance().getDataContext(component);

    DefaultActionGroup editorActionGroup = (DefaultActionGroup)ActionManager.getInstance().getAction(
      "EditorTabActionGroup");
    DefaultActionGroup group = new DefaultActionGroup();

    AnActionEvent event = AnActionEvent.createFromDataContext("EditorTabActionGroup", null, dataContext);

    for (AnAction action : editorActionGroup.getChildren(event)) {
      if(action instanceof ActionGroup) {
        group.addAll(((ActionGroup)action).getChildren(event));
      } else {
        group.addAction(action);
      }
    }
    group.addAction(closeTab, Constraints.LAST);

    tab.setTabLabelActions(group, ActionPlaces.EDITOR_TAB);

    myTabs.addTabSilently(tab, indexToInsert);
  }

  boolean isEmptyVisible() {
    return myTabs.isEmptyVisible();
  }

  public JBTabs getTabs() {
    return myTabs;
  }

  public void requestFocus(boolean forced) {
    if (myTabs != null) {
      IdeFocusManager.getInstance(myProject).requestFocus(myTabs.getComponent(), forced);
    }
  }

  private static class MyQueryable implements Queryable {
    private final TabInfo myTab;

    MyQueryable(TabInfo tab) {
      myTab = tab;
    }

    @Override
    public void putInfo(@NotNull Map<String, String> info) {
      info.put("editorTab", myTab.getText());
    }
  }

  /** @deprecated Use {@link EditorTabPresentationUtil#getEditorTabTitle(Project, VirtualFile, EditorWindow)} */
  @Deprecated
  @NotNull
  public static String calcTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabTitle(project, file, null);
  }

  /** @deprecated Use {@link EditorTabPresentationUtil#getUniqueEditorTabTitle(Project, VirtualFile, EditorWindow)} */
  @Deprecated
  @NotNull
  public static String calcFileName(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getUniqueEditorTabTitle(project, file, null);
  }

  /** @deprecated Use {@link EditorTabPresentationUtil#getEditorTabBackgroundColor(Project, VirtualFile, EditorWindow)} */
  @Deprecated
  @Nullable
  public static Color calcTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    return EditorTabPresentationUtil.getEditorTabBackgroundColor(project, file, null);
  }

  public Component getComponentAt(int i) {
    TabInfo tab = myTabs.getTabAt(i);
    return tab.getComponent();
  }

  private final class MyDataProvider implements DataProvider {
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        VirtualFile selectedFile = myWindow.getSelectedFile();
        return selectedFile != null && selectedFile.isValid() ? selectedFile : null;
      }
      if (EditorWindow.DATA_KEY.is(dataId)) {
        return myWindow;
      }
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return HELP_ID;
      }

      if (CloseAction.CloseTarget.KEY.is(dataId)) {
        TabInfo selected = myTabs.getSelectedInfo();
        if (selected != null) {
          return EditorTabbedContainer.this;
        }
      }

      if (EditorWindow.DATA_KEY.is(dataId)) {
        return myWindow;
      }

      return null;
    }
  }

  @Override
  public void close() {
    TabInfo selected = myTabs.getTargetInfo();
    if (selected == null) return;
    FileEditorManagerEx.getInstanceEx(myProject).closeFile((VirtualFile)selected.getObject(), myWindow);
  }

  private boolean isFloating() {
    return myWindow.getOwner().isFloating();
  }

  private class TabMouseListener extends MouseAdapter {
    private int myActionClickCount;

    @Override
    public void mouseReleased(MouseEvent e) {
      if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
        TabInfo info = myTabs.findInfo(e);
        if (info != null) {
          IdeEventQueue.getInstance().blockNextEvents(e);
          if (e.isAltDown() && e.getButton() == MouseEvent.BUTTON1) {//close others
            List<TabInfo> allTabInfos = myTabs.getTabs();
            for (TabInfo tabInfo : allTabInfos) {
              if (tabInfo == info) continue;
              FileEditorManagerEx.getInstanceEx(myProject).closeFile((VirtualFile)tabInfo.getObject(), myWindow);
            }
          } else {
            FileEditorManagerEx.getInstanceEx(myProject).closeFile((VirtualFile)info.getObject(), myWindow);
          }
        }
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (UIUtil.isActionClick(e)) {
        if (e.getClickCount() == 1) {
          myActionClickCount = 0;
        }
        // clicks on the close window button don't count in determining whether we have a double-click on tab (IDEA-70403)
        Component deepestComponent = SwingUtilities.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
        if (!(deepestComponent instanceof InplaceButton)) {
          myActionClickCount++;
        }
        if (myActionClickCount > 1 && !isFloating()) {
          doHideAll(e);
        }
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (UIUtil.isActionClick(e, MouseEvent.MOUSE_CLICKED) && (e.isMetaDown() || !SystemInfo.isMac && e.isControlDown())) {
        TabInfo info = myTabs.findInfo(e);
        Object o = info == null ? null : info.getObject();
        if (o instanceof VirtualFile) {
          ShowFilePathAction.show((VirtualFile)o, e);
        }
      }
    }
  }

  private static void doHideAll(@NotNull MouseEvent e) {
    if (!Registry.is("editor.maximize.on.double.click")) return;
    ActionManager mgr = ActionManager.getInstance();
    mgr.tryToExecute(mgr.getAction("HideAllWindows"), e, null, ActionPlaces.UNKNOWN, true);
  }

  class MyDragOutDelegate implements TabInfo.DragOutDelegate {

    private VirtualFile myFile;
    private DragSession mySession;

    @Override
    public void dragOutStarted(@NotNull MouseEvent mouseEvent, @NotNull TabInfo info) {
      TabInfo previousSelection = info.getPreviousSelection();
      Image img = JBTabsImpl.getComponentImage(info);
      if (previousSelection == null) {
        previousSelection = myTabs.getToSelectOnRemoveOf(info);
      }
      info.setHidden(true);
      if (previousSelection != null) {
        myTabs.select(previousSelection, true);
      }

      myFile = (VirtualFile)info.getObject();
      Presentation presentation = new Presentation(info.getText());
      presentation.setIcon(info.getIcon());
      mySession = getDockManager().createDragSession(mouseEvent, createDockableEditor(myProject, img, myFile, presentation, myWindow));
    }

    private DockManager getDockManager() {
      return DockManager.getInstance(myProject);
    }

    @Override
    public void processDragOut(@NotNull MouseEvent event, @NotNull TabInfo source) {
      mySession.process(event);
    }

    @Override
    public void dragOutFinished(@NotNull MouseEvent event, TabInfo source) {
      boolean copy = UIUtil.isControlKeyDown(event) || mySession.getResponse(event) == DockContainer.ContentResponse.ACCEPT_COPY;
      if (!copy) {
        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);
        FileEditorManagerEx.getInstanceEx(myProject).closeFile(myFile, myWindow);
      }
      else {
        source.setHidden(false);
      }

      mySession.process(event);
      if (!copy) {
        myFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
      }

      myFile = null;
      mySession = null;
    }

    @Override
    public void dragOutCancelled(TabInfo source) {
      source.setHidden(false);
      if (mySession != null) {
        mySession.cancel();
      }

      myFile = null;
      mySession = null;
    }

  }

  public static class DockableEditor implements DockableContent<VirtualFile> {
    final Image myImg;
    private final DockableEditorTabbedContainer myContainer;
    private final Presentation myPresentation;
    private final Dimension myPreferredSize;
    private final boolean myPinned;
    private final VirtualFile myFile;

    public DockableEditor(Project project, Image img, VirtualFile file, Presentation presentation, Dimension preferredSize, boolean isFilePinned) {
      myImg = img;
      myFile = file;
      myPresentation = presentation;
      myContainer = new DockableEditorTabbedContainer(project);
      myPreferredSize = preferredSize;
      myPinned = isFilePinned;
    }

    @NotNull
    @Override
    public VirtualFile getKey() {
      return myFile;
    }

    @Override
    public Image getPreviewImage() {
      return myImg;
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize;
    }

    @Override
    public String getDockContainerType() {
      return DockableEditorContainerFactory.TYPE;
    }

    @Override
    public Presentation getPresentation() {
      return myPresentation;
    }

    @Override
    public void close() {
      myContainer.close(myFile);
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public boolean isPinned() {
      return myPinned;
    }
  }

  private final class MyTransferHandler extends TransferHandler {
    private final FileDropHandler myFileDropHandler = new FileDropHandler(null);

    @Override
    public boolean importData(JComponent comp, Transferable t) {
      if (myFileDropHandler.canHandleDrop(t.getTransferDataFlavors())) {
        myFileDropHandler.handleDrop(t, myProject, myWindow);
        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
      return myFileDropHandler.canHandleDrop(transferFlavors);
    }
  }

  private static final class EditorTabs extends SingleHeightTabs implements ComponentWithMnemonics {
    @NotNull
    private final EditorWindow myWindow;

    private EditorTabs(Project project, @NotNull Disposable parentDisposable, @NotNull EditorWindow window) {
      super(project, parentDisposable);

      myWindow = window;
      IdeEventQueue.getInstance().addDispatcher(createFocusDispatcher(), parentDisposable);
      setUiDecorator(() -> new UiDecorator.UiDecoration(null, JBUI.insets(0, 8, 0, 8)));

      project.getMessageBus().connect(parentDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
        @Override
        public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          updateActive();
        }

        @Override
        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
          updateActive();
        }

        @Override
        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
          updateActive();
        }
      });
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      drawBorder(g);
    }

    @NotNull
    @Override
    protected TabLabel createTabLabel(@NotNull TabInfo info) {
      return new SingleHeightLabel(this, info) {
        @Override
        protected int getPreferredHeight() {
          Insets insets = getInsets();
          Insets layoutInsets = getLayoutInsets();

          insets.top += layoutInsets.top;
          insets.bottom += layoutInsets.bottom;

          return super.getPreferredHeight() - insets.top - insets.bottom;
        }
      };
    }

    @Override
    protected TabPainterAdapter createTabPainterAdapter() {
      return new EditorTabPainterAdapter();
    }

    @Override
    protected JBTabsBorder createTabBorder() {
      return new JBEditorTabsBorder(this);
    }

    private boolean active;

    @NotNull
    @Override
    public ActionCallback select(@NotNull TabInfo info, boolean requestFocus) {
      active = true;
      return super.select(info, requestFocus);
    }

    private IdeEventQueue.EventDispatcher createFocusDispatcher() {
      return e -> {
        if (e instanceof FocusEvent) {
          updateActive();
        }
        return false;
      };
    }

    private void updateActive() {
      checkActive();
      SwingUtilities.invokeLater(() -> {
        checkActive();
      });
    }

    private void checkActive() {
      boolean newActive = UIUtil.isFocusAncestor(this);

      if(newActive != active) {
        active = newActive;
        revalidateAndRepaint();
      }
    }

    @Override
    protected boolean isActiveTabs(TabInfo info) {
      return active;
    }

    @Nullable
    @Override
    public TabInfo getToSelectOnRemoveOf(TabInfo info) {
      int index = getIndexOf(info);
      if (index != -1) {
        VirtualFile file = myWindow.getFileAt(index);
        int indexToSelect = myWindow.calcIndexToSelect(file, index);
        if (indexToSelect >= 0 && indexToSelect < getTabs().size()) {
          return getTabAt(indexToSelect);
        }
      }
      return super.getToSelectOnRemoveOf(info);
    }

    @Override
    public void revalidateAndRepaint(boolean layoutNow) {
      //noinspection ConstantConditions - called from super constructor
      if (myWindow != null && myWindow.getOwner().isInsideChange()) return;
      super.revalidateAndRepaint(layoutNow);
    }
  }
}
