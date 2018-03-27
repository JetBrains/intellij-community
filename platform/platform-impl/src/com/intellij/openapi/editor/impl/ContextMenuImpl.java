/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author spleaner
 */
public class ContextMenuImpl extends JPanel implements Disposable {
  @NonNls
  public static final String ACTION_GROUP = "EditorContextBarMenu";
  private final JComponent myComponent;
  private final JLayeredPane myLayeredPane;
  private ActionGroup myActionGroup;
  private boolean myVisible = false;
  private boolean myShow = false;
  private int myCurrentOpacity;
  private Timer myTimer;
  private EditorImpl myEditor;
  private boolean myDisposed;
  private ActionToolbar myActionToolbar;

  public ContextMenuImpl(JLayeredPane layeredPane, @NotNull final JScrollPane container, @NotNull final EditorImpl editor) {
    setLayout(new BorderLayout(0, 0));
    myEditor = editor;
    myLayeredPane = layeredPane;

    final ActionManager actionManager = ActionManager.getInstance();

    editor.addEditorMouseListener(new EditorMouseAdapter() {
      @Override
      public void mouseExited(final EditorMouseEvent e) {
        if (!isInsideActivationArea(container, e.getMouseEvent().getPoint())) {
          toggleContextToolbar(false);
        }
      }
    });

    editor.addEditorMouseMotionListener(new EditorMouseMotionAdapter() {
      @Override
      public void mouseMoved(final EditorMouseEvent e) {
        toggleContextToolbar(isInsideActivationArea(container, e.getMouseEvent().getPoint()));
      }
    });

    AnAction action = actionManager.getAction(ACTION_GROUP);
    if (action == null) {
      action = new DefaultActionGroup();
      actionManager.registerAction(ACTION_GROUP, action);
    }

    if (action instanceof ActionGroup) {
      myActionGroup = (ActionGroup)action;
    }

    myComponent = createComponent();
    add(myComponent);

    setVisible(false);
    setOpaque(false);
  }

  private static boolean isInsideActivationArea(JScrollPane container, Point p) {
    final JViewport viewport = container.getViewport();
    final Rectangle r = viewport.getBounds();
    final Point viewPosition = viewport.getViewPosition();

    final Rectangle activationArea = new Rectangle(0, 0, r.width, r.height);
    return activationArea.contains(p.x, p.y - viewPosition.y);
  }

  public static boolean mayShowToolbar(@Nullable final Document document) {
    if (document == null) {
      return false;
    }

    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file != null && file.isValid();
  }

  private void toggleContextToolbar(final boolean show) {
    if (((Container)myActionToolbar).getComponentCount() == 0) {
      return;
    }
    myActionToolbar.updateActionsImmediately();

    if (myShow != show) {
      myShow = show;
      restartTimer();
    }
  }

  private void restartTimer() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = UIUtil.createNamedTimer("Restart context menu", 500, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myDisposed) return;

        if (myTimer != null && myTimer.isRunning()) myTimer.stop();

        myTimer = UIUtil.createNamedTimer("Restart context menu now", 50, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (myShow) {
              if (myVisible) {
                scheduleHide();
                return;
              }

              if (myLayeredPane.getIndexOf(ContextMenuImpl.this) == -1) {
                myCurrentOpacity = 0;
                myLayeredPane.add(ContextMenuImpl.this, JLayeredPane.POPUP_LAYER);
                ContextMenuImpl.this.setVisible(true);
                myLayeredPane.revalidate();
              }

              myCurrentOpacity += 20;
              if (myCurrentOpacity >= 100) {
                myCurrentOpacity = 100;
                myVisible = true;
                myTimer.stop();

                scheduleHide();
              }

              repaint();
            }
            else {
              if (!myVisible) {
                if (myTimer != null && myTimer.isRunning()) myTimer.stop();
                return;
              }

              myCurrentOpacity -= 20;
              if (myCurrentOpacity <= 0) {
                myCurrentOpacity = 0;
                myVisible = false;
                myLayeredPane.remove(ContextMenuImpl.this);
                myLayeredPane.repaint();
              }

              repaint();
            }
          }
        });

        myTimer.setRepeats(true);
        myTimer.start();
      }
    });

    myTimer.setRepeats(false);
    myTimer.start();
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myEditor = null;

    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }

  private void scheduleHide() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = UIUtil.createNamedTimer("Hide context menu", 1500, new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        if (myDisposed) return;

        if (myComponent.isVisible()) {
          final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
          if (pointerInfo != null) {
            final Point location = pointerInfo.getLocation();
            SwingUtilities.convertPointFromScreen(location, myComponent);
            if (!myComponent.getBounds().contains(location)) {
              toggleContextToolbar(false);
            }
            else {
              scheduleHide();
            }
          }
        }
      }
    });

    myTimer.setRepeats(false);
    myTimer.start();
  }

  private JComponent createComponent() {
    myActionToolbar = new ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, myActionGroup, true,
                                            DataManager.getInstance(), ActionManagerEx.getInstanceEx(), KeymapManagerEx.getInstanceEx());
    myActionToolbar.setTargetComponent(myEditor.getContentComponent());
    myActionToolbar.setMinimumButtonSize(new Dimension(22, 22));
    myActionToolbar.setReservePlaceAutoPopupIcon(false);

    ContextMenuPanel contextMenuPanel = new ContextMenuPanel(this);
    contextMenuPanel.setLayout(new BorderLayout(0, 0));
    JComponent toolbarComponent = myActionToolbar.getComponent();
    toolbarComponent.setOpaque(false);
    contextMenuPanel.add(toolbarComponent);

    return contextMenuPanel;
  }

  private static class ContextMenuPanel extends JPanel {
    private final ContextMenuImpl myContextMenu;

    private ContextMenuPanel(final ContextMenuImpl contextMenu) {
      myContextMenu = contextMenu;
      setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
      setOpaque(false);
    }

    @Override
    protected void paintChildren(final Graphics g) {
      Graphics2D graphics = (Graphics2D)g.create();
      try {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, myContextMenu.myCurrentOpacity / 100.0f));
        super.paintChildren(graphics);
      }
      finally {
        graphics.dispose();
      }
    }

    @Override
    public void paint(Graphics g) {
      paintComponent(g);
      super.paint(g);
    }

    @Override
    public void paintComponent(final Graphics g) {
      Rectangle r = getBounds();
      Graphics2D graphics = (Graphics2D)g.create();
      try {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myContextMenu.myCurrentOpacity / 600.0f));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.GRAY);
        graphics.fillRoundRect(0, 0, r.width - 1, r.height - 1, 6, 6);
      }
      finally {
        graphics.dispose();
      }
    }
  }
}
