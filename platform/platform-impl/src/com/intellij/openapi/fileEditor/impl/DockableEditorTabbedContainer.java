// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actions.DragEditorTabsFusEventFields;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.events.ObjectEventData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLayout;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import kotlin.Unit;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import static javax.swing.SwingConstants.*;

public final class DockableEditorTabbedContainer implements DockContainer.Persistent, Activatable, Disposable {
  private final @NotNull EditorsSplitters splitters;

  private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

  private JBTabs currentOver;
  private Image currentOverImg;
  private TabInfo currentOverInfo;
  private AbstractPainter currentPainter;
  private Disposable glassPaneListenersDisposable = Disposer.newDisposable();

  private final boolean disposeWhenEmpty;

  private boolean wasEverShown;

  DockableEditorTabbedContainer(@NotNull EditorsSplitters splitters, boolean disposeWhenEmpty) {
    this.splitters = splitters;
    this.disposeWhenEmpty = disposeWhenEmpty;
  }

  @Override
  public void dispose() {
    Disposer.dispose(splitters);
  }

  @Override
  public String getDockContainerType() {
    return DockableEditorContainerFactory.TYPE;
  }

  @Override
  public Element getState() {
    Element editors = new Element("state");
    splitters.writeExternal(editors);
    return editors;
  }

  void fireContentClosed(@NotNull VirtualFile file) {
    for (Listener each : listeners) {
      each.contentRemoved(file);
    }
  }

  void fireContentOpen(@NotNull VirtualFile file) {
    for (Listener each : listeners) {
      each.contentAdded(file);
    }
  }

  @Override
  public @NotNull RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(splitters);
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

    JBTabs targetTabs = splitters.getTabsAt(point);
    if (targetTabs != null) {
      return targetTabs;
    }
    else {
      EditorWindow window = splitters.getCurrentWindow();
      if (window != null) {
        return window.getTabbedPane().getTabs();
      }
      else {
        EditorWindow[] windows = splitters.getWindows();
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
    Integer dragStartLocation = file.getUserData(EditorWindow.Companion.getDRAG_START_LOCATION_HASH_KEY$intellij_platform_ide_impl());
    boolean sameWindow = currentOver != null && dragStartLocation != null && dragStartLocation == System.identityHashCode(currentOver);
    int dropSide = getCurrentDropSide();
    if (currentOver != null) {
      final DataProvider provider = currentOver.getDataProvider();
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
      window = splitters.getOrCreateCurrentWindow(file);//drag outside
    }

    Boolean dropInBetweenPinnedTabs = null;
    boolean dropInPinnedRow = false;
    final int index = currentOver != null ? ((JBTabsEx)currentOver).getDropInfoIndex() : -1;
    if (currentOver != null && AdvancedSettings.getBoolean("editor.keep.pinned.tabs.on.left")) {
      if (index >= 0 && index <= currentOver.getTabCount()) {
        TabInfo tabInfo = index == currentOver.getTabCount() ? null : currentOver.getTabAt(index);
        TabInfo previousInfo = index > 0 ? currentOver.getTabAt(index - 1) : null;
        boolean previousIsPinned = previousInfo != null && previousInfo.isPinned();
        if (file.getUserData(EditorWindow.Companion.getDRAG_START_PINNED_KEY$intellij_platform_ide_impl()) == Boolean.TRUE) {
          dropInBetweenPinnedTabs = index == 0 || (tabInfo != null && tabInfo.isPinned()) || previousIsPinned;
        }
        else {
          dropInBetweenPinnedTabs = tabInfo != null ? tabInfo.isPinned() : null;
        }
        if (index > 0 && previousIsPinned) {
          Component previousLabel = currentOver.getTabLabel(previousInfo);
          Rectangle bounds = previousLabel.getBounds();
          Point dropPoint = dropTarget.getPoint(previousLabel);
          dropInPinnedRow =
            currentOver instanceof JBTabsImpl
            && TabLayout.showPinnedTabsSeparately()
            && ((JBTabsImpl)currentOver).getTabsPosition() == JBTabsPosition.top
            && bounds.y < dropPoint.y && bounds.getMaxY() > dropPoint.y;
        }
      }
      Integer dragStartIndex = file.getUserData(EditorWindow.Companion.getDRAG_START_INDEX_KEY$intellij_platform_ide_impl());
      boolean isDroppedToOriginalPlace = dragStartIndex != null && dragStartIndex == index && sameWindow;
      if (!isDroppedToOriginalPlace) {
        file.putUserData(EditorWindow.Companion.getDRAG_START_PINNED_KEY$intellij_platform_ide_impl(), dropInBetweenPinnedTabs);
      }
      if (dropInPinnedRow) {
        file.putUserData(EditorWindow.Companion.getDRAG_START_INDEX_KEY$intellij_platform_ide_impl(), index + 1);
        file.putUserData(EditorWindow.Companion.getDRAG_START_PINNED_KEY$intellij_platform_ide_impl(), Boolean.TRUE);
        dropInBetweenPinnedTabs = true;
      }
    }
    recordDragStats(dropIntoNewlyCreatedWindow ? -1 : CENTER, sameWindow);
    FileEditorOpenOptions openOptions = new FileEditorOpenOptions()
      .withIndex(index)
      .withRequestFocus();
    splitters.getManager().openFileImpl2(window, file, openOptions);
    window.setFilePinned(file, Objects.requireNonNullElseGet(dropInBetweenPinnedTabs, dockableEditor::isPinned));
  }

  private void recordDragStats(int dropSide, boolean sameWindow) {
    String actionId = switch (dropSide) {
      case -1 -> "OpenElementInNewWindow";
      case TOP -> "SplitVertically";
      case LEFT -> "SplitHorizontally";
      case BOTTOM -> "MoveTabDown";
      case RIGHT -> "MoveTabRight";
      case CENTER -> null; // This drag-n-drop gesture cannot be mapped to any action (drop to some exact tab index)
      default -> null;
    };
    if (actionId != null) {
      AnActionEvent event = AnActionEvent.createFromInputEvent(
        new MouseEvent(splitters, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false,
                       MouseEvent.BUTTON1), ActionPlaces.EDITOR_TAB, null, DataContext.EMPTY_CONTEXT);
      ActionsCollectorImpl.recordActionInvoked(
        splitters.getManager().getProject(), ActionManager.getInstance().getAction(actionId), event,
        (list) -> {
          list.add(ActionsEventLogGroup.ADDITIONAL.with(
            new ObjectEventData(DragEditorTabsFusEventFields.SAME_WINDOW.with(sameWindow))));
          return Unit.INSTANCE;
        });
    }
  }

  @MagicConstant(intValues = {CENTER, TOP, LEFT, BOTTOM, RIGHT, -1})
  public int getCurrentDropSide() {
    return currentOver instanceof JBTabsEx ? ((JBTabsEx)currentOver).getDropSide() : -1;
  }

  @Override
  public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
    JBTabs current = getTabsAt(content, point);

    if (currentOver != null && currentOver != current) {
      resetDropOver(content);
    }

    if (currentOver == null && current != null) {
      currentOver = current;
      Presentation presentation = content.getPresentation();
      currentOverInfo = new TabInfo(new JLabel("")).setText(presentation.getText()).setIcon(presentation.getIcon());
      currentOverImg = currentOver.startDropOver(currentOverInfo, point);
    }

    if (currentOver != null) {
      currentOver.processDropOver(currentOverInfo, point);
    }
    if (currentPainter == null) {
      currentPainter = new MyDropAreaPainter();
      glassPaneListenersDisposable = Disposer.newDisposable("GlassPaneListeners");
      Disposer.register(splitters, glassPaneListenersDisposable);
      IdeGlassPaneUtil.find(currentOver.getComponent())
        .addPainter(currentOver.getComponent(), currentPainter, glassPaneListenersDisposable);
    }
    if (currentPainter instanceof MyDropAreaPainter) {
      ((MyDropAreaPainter)currentPainter).processDropOver();
    }

    return currentOverImg;
  }

  @Override
  public void resetDropOver(@NotNull DockableContent content) {
    if (currentOver != null) {
      currentOver.resetDropOver(currentOverInfo);
      currentOver = null;
      currentOverInfo = null;
      currentOverImg = null;

      Disposer.dispose(glassPaneListenersDisposable);
      glassPaneListenersDisposable = Disposer.newDisposable();
      currentPainter = null;
    }
  }

  @Override
  public @NotNull JComponent getContainerComponent() {
    return splitters;
  }

  public @NotNull EditorsSplitters getSplitters() {
    return splitters;
  }

  public void close(@NotNull VirtualFile file) {
    splitters.closeFile(file, false);
  }

  @Override
  public void closeAll() {
    for (VirtualFile each : splitters.getOpenFileList()) {
      close(each);
    }
  }

  @Override
  public void addListener(@NotNull Listener listener, Disposable parent) {
    listeners.add(listener);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        listeners.remove(listener);
      }
    });
  }

  @Override
  public boolean isEmpty() {
    return splitters.isEmptyVisible();
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return disposeWhenEmpty;
  }

  @Override
  public void showNotify() {
    if (!wasEverShown) {
      wasEverShown = true;
      getSplitters().openFilesAsync();
    }
  }

  private final class MyDropAreaPainter extends AbstractPainter {
    private Shape boundingBox;

    @Override
    public boolean needsRepaint() {
      return boundingBox != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (boundingBox == null) {
        return;
      }
      GraphicsUtil.setupAAPainting(g);
      g.setColor(JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND);
      g.fill(boundingBox);
    }

    private void processDropOver() {
      boundingBox = null;
      setNeedsRepaint(true);

      Rectangle r = currentOver.getDropArea();
      int currentDropSide = getCurrentDropSide();
      if (currentDropSide == -1) {
        return;
      }
      TabsUtil.updateBoundsWithDropSide(r, currentDropSide);
      boundingBox = new Rectangle2D.Double(r.x, r.y, r.width, r.height);
    }
  }

  @Override
  public String toString() {
    return "DockableEditorTabbedContainer windows=" + Arrays.toString(splitters.getWindows());
  }
}
