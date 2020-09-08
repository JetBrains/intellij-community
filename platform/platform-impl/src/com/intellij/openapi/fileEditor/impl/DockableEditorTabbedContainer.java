// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsEx;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.update.Activatable;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.CopyOnWriteArraySet;

import static javax.swing.SwingConstants.*;

public final class DockableEditorTabbedContainer implements DockContainer.Persistent, Activatable {
  private final EditorsSplitters mySplitters;
  private final Project myProject;

  private final CopyOnWriteArraySet<Listener> myListeners = new CopyOnWriteArraySet<>();

  private JBTabs myCurrentOver;
  private Image myCurrentOverImg;
  private TabInfo myCurrentOverInfo;
  private MyDropAreaPainter myCurrentPainter;
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
    return getTabsAt(content, point) != null ? ContentResponse.ACCEPT_MOVE : ContentResponse.DENY;
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
    int dropSide = getCurrentDropSide();
    if (myCurrentOver != null) {
      final DataProvider provider = myCurrentOver.getDataProvider();
      if (provider != null) {
        window = EditorWindow.DATA_KEY.getData(provider);
      }
      if (window != null && dropSide != -1) {
        window.split(dropSide == BOTTOM || dropSide == TOP ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
                     true, file, false, dropSide != LEFT && dropSide != TOP);
        return;
      }
    }

    if (window == null || window.isDisposed()) {
      window = mySplitters.getOrCreateCurrentWindow(file);
    }


    if (myCurrentOver != null) {
      int index = ((JBTabsEx)myCurrentOver).getDropInfoIndex();
      file.putUserData(EditorWindow.INITIAL_INDEX_KEY, index);
    }

    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(myProject)).openFileImpl2(window, file, true);
    window.setFilePinned(file, dockableEditor.isPinned());
  }

  @MagicConstant(intValues = {TOP, LEFT, BOTTOM, RIGHT, -1})
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
    myCurrentPainter.processDropOver();

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
    private final Color myColor = JBColor.namedColor("dropArea.base", 0x4f4fff, 0x5081c0);

    @Override
    public boolean needsRepaint() {
      return myBoundingBox != null;
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (myBoundingBox == null) return;
      GraphicsUtil.setupAAPainting(g);
      g.setColor(ColorUtil.toAlpha(myColor, 200));
      g.setStroke(new BasicStroke(2));
      g.draw(myBoundingBox);
      g.setColor(ColorUtil.toAlpha(myColor, 40));
      g.fill(myBoundingBox);
    }

    private void processDropOver() {
      myBoundingBox = null;
      setNeedsRepaint(true);

      Rectangle r = new Rectangle(myCurrentOver.getComponent().getBounds());
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
        case RIGHT:
          r.width /= 2;
          r.x += r.width;
          break;
        case BOTTOM:
          r.height /= 2;
          r.y += r.height;
          break;
      }
      myBoundingBox = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 16, 16);
    }
  }
}
