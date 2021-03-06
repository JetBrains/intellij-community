// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.events.ObjectEventData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.update.Activatable;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.intellij.ide.actions.DragEditorTabsFusEventFields.SAME_WINDOW;
import static javax.swing.SwingConstants.*;

public final class DockableEditorTabbedContainer implements DockContainer.Persistent, Activatable {
  private final EditorsSplitters mySplitters;
  private final Project myProject;

  private final CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<>();

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;
  private AbstractPainter myCurrentPainter;
  private Disposable myGlassPaneListenersDisposable = Disposer.newDisposable();

  private final boolean myDisposeWhenEmpty;

  private boolean myWasEverShown;

  DockableEditorTabbedContainer(Project project) {
    this(project, null, true);
  }

  DockableEditorTabbedContainer(Project project, @Nullable EditorsSplitters splitters, boolean disposeWhenEmpty) {
    myProject = project;
    mySplitters = splitters;
    myDisposeWhenEmpty = disposeWhenEmpty;
  }

  @Override
  public String getDockContainerType() {
    return DockableEditorContainerFactory.TYPE;
  }

  @Override
  public Element getState() {
    Element editors = new Element("state");
    mySplitters.writeExternal(editors);
    return editors;
  }

  void fireContentClosed(@NotNull VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentRemoved(file);
    }
  }

  void fireContentOpen(@NotNull VirtualFile file) {
    for (Listener each : myListeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public @NotNull RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(mySplitters);
  }

  @Override
  public @NotNull RelativeRectangle getAcceptAreaFallback() {
    JRootPane root = mySplitters.getRootPane();
    return root != null ? new RelativeRectangle(root) : new RelativeRectangle(mySplitters);
  }

  @Override
  public @NotNull ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    JBTabs tabs = getTabsAt(content, point);
    return tabs != null && !tabs.getPresentation().isHideTabs() ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
  }

  private @Nullable JBTabs getTabsAt(DockableContent<?> content, RelativePoint point) {
    if (!(content instanceof EditorTabbedContainer.DockableEditor)) {
      return null;
    }

    JBTabs targetTabs = mySplitters.getTabsAt(point);
    if (targetTabs != null) {
      return targetTabs;
    }
    else {
      EditorWindow window = mySplitters.getCurrentWindow();
      if (window != null) {
        return window.getTabbedPane().getTabs();
      }
      else {
        EditorWindow[] windows = mySplitters.getWindows();
        for (EditorWindow each : windows) {
          if (each.getTabbedPane().getTabs() != null) {
            return each.getTabbedPane().getTabs();
          }
        }
      }
    }

    return null;
  }

  @Override
  public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
    EditorWindow window = null;
    final EditorTabbedContainer.DockableEditor dockableEditor = (EditorTabbedContainer.DockableEditor)content;
    VirtualFile file = dockableEditor.getFile();
    Integer dragStartLocation = file.getUserData(EditorWindow.DRAG_START_LOCATION_HASH_KEY);
    boolean sameWindow = myCurrentOver != null && dragStartLocation != null && dragStartLocation == System.identityHashCode(myCurrentOver);
    int dropSide = getCurrentDropSide();
    if (myCurrentOver != null) {
      final DataProvider provider = myCurrentOver.getDataProvider();
      if (provider != null) {
        window = EditorWindow.DATA_KEY.getData(provider);
      }
      if (window != null && dropSide != -1 && dropSide != CENTER) {
        window.split(dropSide == BOTTOM || dropSide == TOP ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
                     true, file, true, dropSide != LEFT && dropSide != TOP);
        recordDragStats(dropSide, false);
        return;
      }
    }
    boolean dropIntoNewlyCreatedWindow = false;
    if (window == null || window.isDisposed()) {
      dropIntoNewlyCreatedWindow = true;
      window = mySplitters.getOrCreateCurrentWindow(file);//drag outside
    }

    Boolean dropInBetweenPinnedTabs = null;
    if (myCurrentOver != null) {

      int index = ((JBTabsEx)myCurrentOver).getDropInfoIndex();
      if (index >= 0 && index <= myCurrentOver.getTabCount()) {
        TabInfo tabInfo = index == myCurrentOver.getTabCount() ? null : myCurrentOver.getTabAt(index);
        if (file.getUserData(EditorWindow.DRAG_START_PINNED_KEY) == Boolean.TRUE) {
          dropInBetweenPinnedTabs = index == 0 || (tabInfo != null && tabInfo.isPinned()) || myCurrentOver.getTabAt(index - 1).isPinned();
        }
        else {
          dropInBetweenPinnedTabs = tabInfo != null ? tabInfo.isPinned() : null;
        }
      }
      file.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
      Integer dragStartIndex = file.getUserData(EditorWindow.DRAG_START_INDEX_KEY);
      boolean isDroppedToOriginalPlace = dragStartIndex != null && dragStartIndex == index && sameWindow;
      if (!isDroppedToOriginalPlace) {
        file.putUserData(EditorWindow.DRAG_START_PINNED_KEY, dropInBetweenPinnedTabs);
      }
    }
    recordDragStats(dropIntoNewlyCreatedWindow? -1  : CENTER, sameWindow);
    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(window, file, true);
    window.setFilePinned(file, Objects.requireNonNullElseGet(dropInBetweenPinnedTabs, dockableEditor::isPinned));
  }

  private void recordDragStats(int dropSide, boolean sameWindow) {
    String actionId = null;
    switch (dropSide) {
      case -1: actionId = "OpenElementInNewWindow";break;
      case TOP: actionId = "SplitVertically";break;
      case LEFT: actionId = "SplitHorizontally"; break;
      case BOTTOM: actionId = "MoveTabDown"; break;
      case RIGHT: actionId = "MoveTabRight";break;
      case CENTER: return;// This drag-n-drop gesture cannot be mapped to any action (drop to some exact tab index)
    }
    if (actionId != null && mySplitters != null) {
      AnActionEvent event = AnActionEvent.createFromInputEvent(
        new MouseEvent(mySplitters, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false,
                       MouseEvent.BUTTON1), ActionPlaces.EDITOR_TAB, null, DataContext.EMPTY_CONTEXT);
      ActionsCollectorImpl.recordActionInvoked(myProject, ActionManager.getInstance().getAction(actionId), event,
                                               Collections.singletonList(ActionsEventLogGroup.ADDITIONAL.with(
                                                 new ObjectEventData(SAME_WINDOW.with(sameWindow)))));
    }
  }

  @MagicConstant(intValues = {CENTER, TOP, LEFT, BOTTOM, RIGHT, -1})
  public int getCurrentDropSide() {
    return myCurrentOver instanceof JBTabsEx ? ((JBTabsEx)myCurrentOver).getDropSide() : -1;
  }

  @Override
  public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
    JBTabs current = getTabsAt(content, point);

    if (myCurrentOver != null && myCurrentOver != current) {
      resetDropOver(content);
    }

    if (myCurrentOver == null && current != null) {
      myCurrentOver = current;
      Presentation presentation = content.getPresentation();
      myCurrentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      myCurrentOverImg = myCurrentOver.startDropOver(myCurrentOverInfo, point);
    }

    if (myCurrentOver != null) {
      myCurrentOver.processDropOver(myCurrentOverInfo, point);
    }
    if (myCurrentPainter == null) {
      myCurrentPainter = new MyDropAreaPainter();
      myGlassPaneListenersDisposable = Disposer.newDisposable("GlassPaneListeners");
      Disposer.register(mySplitters.parentDisposable, myGlassPaneListenersDisposable);
      IdeGlassPaneUtil.find(myCurrentOver.getComponent()).addPainter(myCurrentOver.getComponent(), myCurrentPainter, myGlassPaneListenersDisposable);
    }
    if (myCurrentPainter instanceof MyDropAreaPainter) {
      ((MyDropAreaPainter)myCurrentPainter).processDropOver();
    }

    return myCurrentOverImg;
  }

  @Override
  public void resetDropOver(@NotNull DockableContent content) {
    if (myCurrentOver != null) {
      myCurrentOver.resetDropOver(myCurrentOverInfo);
      myCurrentOver = null;
      myCurrentOverInfo = null;
      myCurrentOverImg = null;

      Disposer.dispose(myGlassPaneListenersDisposable);
      myGlassPaneListenersDisposable = Disposer.newDisposable();
      myCurrentPainter = null;
    }
  }

  @Override
  public JComponent getContainerComponent() {
    return mySplitters;
  }

  public EditorsSplitters getSplitters() {
    return mySplitters;
  }

  public void close(@NotNull VirtualFile file) {
    mySplitters.closeFile(file, false);
  }

  @Override
  public void closeAll() {
    for (VirtualFile each : mySplitters.getOpenFileList()) {
      close(each);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener, Disposable parent) {
    myListeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  @Override
  public boolean isEmpty() {
    return mySplitters.isEmptyVisible();
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return myDisposeWhenEmpty;
  }

  @Override
  public void showNotify() {
    if (!myWasEverShown) {
      myWasEverShown = true;
      getSplitters().openFiles();
    }
  }

  private class MyDropAreaPainter extends AbstractPainter {
    private Shape myBoundingBox;

    @Override
    public boolean needsRepaint() {
      return myBoundingBox != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (myBoundingBox == null) return;
      GraphicsUtil.setupAAPainting(g);
      g.setColor(JBColor.namedColor("DragAndDrop.areaBackground", 0x3d7dcc, 0x404a57));
      g.fill(myBoundingBox);
    }

    private void processDropOver() {
      myBoundingBox = null;
      setNeedsRepaint(true);

      Rectangle r = TabsUtil.getDropArea(myCurrentOver);
      int currentDropSide = getCurrentDropSide();
      if (currentDropSide == -1) {
        return;
      }
      switch (currentDropSide) {
        case TOP:
          r.height /= 2;
          break;
        case LEFT:
          r.width /= 2;
          break;
        case BOTTOM:
          int h = r.height / 2;
          r.height -= h;
          r.y += h;
          break;
        case RIGHT:
          int w = r.width / 2;
          r.width -= w;
          r.x += w;
          break;
      }
      myBoundingBox = new Rectangle2D.Double(r.x, r.y, r.width, r.height);
    }
  }
}
