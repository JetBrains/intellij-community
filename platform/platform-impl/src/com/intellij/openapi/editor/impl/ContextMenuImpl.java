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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 */
public class ContextMenuImpl extends JPanel implements Disposable {
  @NonNls
  public static final String ACTION_GROUP = "EditorContextBarMenu";

  private ActionGroup myActionGroup;
  private final JComponent myComponent;
  private boolean myVisible = false;
  private boolean myShow = false;
  private int myCurrentOpacity;
  private Timer myTimer;
  private EditorImpl myEditor;
  private ContextMenuPanel myContextMenuPanel;
  private boolean myDisposed;
  private final JLayeredPane myLayeredPane;

  public ContextMenuImpl(JLayeredPane layeredPane, @NotNull final JBScrollPane container, @NotNull final EditorImpl editor) {
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

    AnAction action = actionManager.getAction("EditorContextBarMenu");
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

  private static boolean isInsideActivationArea(JBScrollPane container, Point p) {
    final JViewport viewport = container.getViewport();
    final Rectangle r = viewport.getBounds();
    final Point viewPosition = viewport.getViewPosition();

    final Rectangle activationArea = new Rectangle(0, 0, r.width, 150);
    return activationArea.contains(p.x, p.y - viewPosition.y);
  }

  private void toggleContextToolbar(final boolean show) {
    final Component toolbar = myComponent.getComponent(0);
    final int count = ((Container)toolbar).getComponentCount();
    if (count == 0) {
      return;
    }

    if (myShow != show) {
      myShow = show;
      restartTimer();
    }
  }

  private void restartTimer() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = new Timer(500, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myDisposed) return;

        if (myTimer != null && myTimer.isRunning()) myTimer.stop();

        myTimer = new Timer(50, new ActionListener() {
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
              if (myCurrentOpacity > 100) {
                myCurrentOpacity = 100;
                myVisible = true;
                myTimer.stop();

                scheduleHide();
              }

              repaint();
            } else {
              if (!myVisible) {
                if (myTimer != null && myTimer.isRunning()) myTimer.stop();
                return;
              }

              myCurrentOpacity -= 20;
              if (myCurrentOpacity < 0) {
                myCurrentOpacity = 0;
                myVisible = false;
                myLayeredPane.remove(ContextMenuImpl.this);
                myLayeredPane.revalidate();
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

  public void dispose() {
    myDisposed = true;
    myEditor = null;

    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }

  }

  public static boolean mayShowToolbar(@Nullable final Document document) {
    if (document == null) {
      return false;
    }

    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file != null && file.isValid() && (file.getFileSystem() == LocalFileSystem.getInstance() || file instanceof HttpVirtualFile);
  }

  private void scheduleHide() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = new Timer(1500, new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (myDisposed) return;

        if (myComponent.isVisible()) {
          final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
          if (pointerInfo != null) {
            final Point location = pointerInfo.getLocation();
            SwingUtilities.convertPointFromScreen(location, myComponent);
            if (!myComponent.getBounds().contains(location)) {
              toggleContextToolbar(false);
            } else {
              scheduleHide();
            }
          }
        }
      }
    });

    myTimer.setRepeats(false);
    myTimer.start();
  }

  private ActionToolbar createToolbar(final ActionGroup group) {
    final ActionToolbarImpl actionToolbar =
      new ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, group, true, DataManager.getInstance(), ActionManagerEx.getInstanceEx(),
                            KeymapManagerEx.getInstanceEx()) {

        @Override
        public void paint(final Graphics g) {
          if (myContextMenuPanel.isPaintChildren()) {
            paintChildren(g);
          }
        }

        @Override
        protected void paintChildren(final Graphics g) {
          if (myContextMenuPanel.isPaintChildren()) {
            super.paintChildren(g);
          }
        }

        @Override
        public boolean isOpaque() {
          return myContextMenuPanel.isPaintChildren();
        }

        @Override
        public ActionButton createToolbarButton(final AnAction action,
                                                final ActionButtonLook look,
                                                final String place,
                                                final Presentation presentation,
                                                final Dimension minimumSize) {
          final ActionButton result = new ActionButton(action, presentation, place, minimumSize) {
            @Override
            public void paintComponent(final Graphics g) {
              if (myContextMenuPanel.isPaintChildren()) {
                final ActionButtonLook look = getButtonLook();
                look.paintIcon(g, this, getIcon());
              }

              if (myContextMenuPanel.isShown() && getPopState() == ActionButton.POPPED) {
                final ActionButtonLook look = getButtonLook();
                look.paintBackground(g, this);
                look.paintIcon(g, this, getIcon());
              }
            }

            @Override
            public boolean isOpaque() {
              return myContextMenuPanel.isPaintChildren() || getPopState() == ActionButton.POPPED;
            }

            @Override
            public void paint(final Graphics g) {
              final Graphics2D g2 = (Graphics2D)g;
              paintComponent(g2);
            }
          };

          result.setLook(look);
          return result;
        }
      };

    actionToolbar.setTargetComponent(myEditor.getContentComponent());

    return actionToolbar;
  }

  private JComponent createComponent() {
    final ActionToolbar toolbar = createToolbar(myActionGroup);
    toolbar.setMinimumButtonSize(new Dimension(20, 20));
    toolbar.setReservePlaceAutoPopupIcon(false);

    myContextMenuPanel = new ContextMenuPanel(this);
    myContextMenuPanel.setLayout(new BorderLayout(0, 0));
    myContextMenuPanel.add(toolbar.getComponent());

    return myContextMenuPanel;
  }

  private static class ContextMenuPanel extends JPanel {
    private final ContextMenuImpl myContextMenu;
    private BufferedImage myBufferedImage;
    private boolean myPaintChildren = false;

    private ContextMenuPanel(final ContextMenuImpl contextMenu) {
      myContextMenu = contextMenu;
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      setOpaque(false);
    }

    @Override
    public void invalidate() {
      super.invalidate();

      myBufferedImage = null;
    }

    @Override
    public void revalidate() {
      super.revalidate();

      myBufferedImage = null;
    }

    @Override
    protected void paintChildren(final Graphics g) {
      if (myPaintChildren) {
        super.paintChildren(g);
      }
    }

    public boolean isPaintChildren() {
      return myPaintChildren;
    }

    @Override
    public void paint(final Graphics g) {
      final Rectangle r = getBounds();
      if (myBufferedImage == null) {
        myBufferedImage = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);

        final Graphics graphics = myBufferedImage.getGraphics();
        final Graphics2D g2d2 = (Graphics2D)graphics;
        final Composite old = g2d2.getComposite();

        g2d2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

        g2d2.setColor(Color.GRAY);
        g2d2.fillRoundRect(0, 0, r.width - 1, r.height - 1, 6, 6);

        g2d2.setComposite(old);

        myPaintChildren = true;
        paintChildren(g2d2);
        myPaintChildren = false;
      }

      final Graphics2D g2 = (Graphics2D)g;
      final Composite old = g2.getComposite();

      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, myContextMenu.myCurrentOpacity / 100.0f));
      g2.drawImage(myBufferedImage, 0, 0, myBufferedImage.getWidth(null), myBufferedImage.getHeight(null), null);

      g2.setComposite(old);
    }

    public boolean isShown() {
      return myContextMenu.myCurrentOpacity == 100;
    }
  }
}
