/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltip;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.impl.ShadowBorderPainter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.FocusRequestor;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Range;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class BalloonImpl implements Balloon, IdeTooltip.Ui {

  private MyComponent myComp;
  private JLayeredPane myLayeredPane;
  private AbstractPosition myPosition;
  private Point myTargetPoint;
  private final boolean myHideOnFrameResize;

  private final Color myBorderColor;
  private final Color myFillColor;

  private final Insets myContainerInsets;

  private boolean myLastMoveWasInsideBalloon;

  private Rectangle myForcedBounds;

  private CloseButton myCloseRec;

  private final AWTEventListener myAwtActivityListener = new AWTEventListener() {
    public void eventDispatched(final AWTEvent event) {
      if (myHideOnMouse &&
          (event.getID() == MouseEvent.MOUSE_PRESSED)) {
        final MouseEvent me = (MouseEvent)event;
        if (isInsideBalloon(me))  return;

        hide();
        return;
      }

      if (myClickHandler != null && event.getID() == MouseEvent.MOUSE_CLICKED) {
        final MouseEvent me = (MouseEvent)event;
        if (!(me.getComponent() instanceof CloseButton) && isInsideBalloon(me)) {
          myClickHandler.actionPerformed(new ActionEvent(BalloonImpl.this, ActionEvent.ACTION_PERFORMED, "click", me.getModifiersEx()));
          if (myCloseOnClick) {
            hide();
            return;
          }
        }
      }

      if (myEnableCloseButton && event.getID() == MouseEvent.MOUSE_MOVED) {
        final MouseEvent me = (MouseEvent)event;
        final boolean inside = isInsideBalloon(me);
        final boolean moveChanged = inside != myLastMoveWasInsideBalloon;
        myLastMoveWasInsideBalloon = inside;
        if (moveChanged) {
          myComp.repaintButton();
        }
      }

      if (event instanceof MouseEvent && UIUtil.isCloseClick((MouseEvent)event)) {
        hide();
        return;
      }

      if (myHideOnKey && (event.getID() == KeyEvent.KEY_PRESSED)) {
        final KeyEvent ke = (KeyEvent)event;
        if (ke.getKeyCode() != KeyEvent.VK_SHIFT && ke.getKeyCode() != KeyEvent.VK_CONTROL && ke.getKeyCode() != KeyEvent.VK_ALT && ke.getKeyCode() != KeyEvent.VK_META) {
          if (SwingUtilities.isDescendingFrom(ke.getComponent(), myComp) || ke.getComponent() == myComp) return;
          hide();
        }
      }
    }
  };
  private final long myFadeoutTime;
  private Dimension myDefaultPrefSize;
  private final ActionListener myClickHandler;
  private final boolean myCloseOnClick;

  private final CopyOnWriteArraySet<JBPopupListener> myListeners = new CopyOnWriteArraySet<JBPopupListener>();
  private boolean myVisible;
  private PositionTracker<Balloon> myTracker;
  private int myAnimationCycle = 500;

  private boolean myFadedIn;
  private boolean myFadedOut;
  private int myCalloutshift;

  private int myPositionChangeXShift;
  private int myPositionChangeYShift;
  private boolean myDialogMode;
  private IdeFocusManager myFocusManager;
  private String myTitle;
  private JLabel myTitleLabel;

  private boolean myAnimationEnabled = true;
  private boolean myShadow = false;
  private Layer myLayer;
  private Point myPrevMousePoint = null;

  public boolean isInsideBalloon(MouseEvent me) {
    return isInside(new RelativePoint(me));
  }

  @Override
  public boolean isInside(RelativePoint target) {
    Component cmp = target.getOriginalComponent();

    if (!cmp.isShowing()) return true;
    if (cmp == myCloseRec) return true;
    if (SwingUtilities.isDescendingFrom(cmp, myComp) || cmp == myComp) return true;
    if (myComp == null || !myComp.isShowing()) return false;
    Rectangle rectangleOnScreen = new Rectangle(myComp.getLocationOnScreen(), myComp.getSize());
    if (rectangleOnScreen.contains(target.getScreenPoint())) return true;

    try {
      return ScreenUtil.isMovementTowards(myPrevMousePoint, target.getScreenPoint(), rectangleOnScreen);
    }
    finally {
      myPrevMousePoint = target.getScreenPoint();
    }
  }

  private final ComponentAdapter myComponentListener = new ComponentAdapter() {
    public void componentResized(final ComponentEvent e) {
      if (myHideOnFrameResize) {
        hide();
      }
    }
  };
  private Animator myAnimator;
  private boolean myShowPointer;

  private boolean myDisposed;
  private final JComponent myContent;
  private boolean myHideOnMouse;
  private final boolean myHideOnKey;
  private boolean myHideOnAction;
  private final boolean myEnableCloseButton;
  private final Icon myCloseButton = IconLoader.getIcon("/general/balloonClose.png");

  public BalloonImpl(JComponent content,
                     Color borderColor,
                     Color fillColor,
                     boolean hideOnMouse,
                     boolean hideOnKey,
                     boolean hideOnAction,
                     boolean showPointer,
                     boolean enableCloseButton,
                     long fadeoutTime,
                     boolean hideOnFrameResize,
                     ActionListener clickHandler,
                     boolean closeOnClick,
                     int animationCycle,
                     int calloutShift,
                     int positionChangeXShift,
                     int positionChangeYShift,
                     boolean dialogMode,
                     String title,
                     Insets contentInsets,
                     boolean shadow,
                     boolean smallVariant,
                     Layer layer) {
    myBorderColor = borderColor;
    myFillColor = fillColor;
    myContent = content;
    myHideOnMouse = hideOnMouse;
    myHideOnKey = hideOnKey;
    myHideOnAction = hideOnAction;
    myShowPointer = showPointer;
    myEnableCloseButton = enableCloseButton;
    myHideOnFrameResize = hideOnFrameResize;
    myClickHandler = clickHandler;
    myCloseOnClick = closeOnClick;
    myCalloutshift = calloutShift;
    myPositionChangeXShift = positionChangeXShift;
    myPositionChangeYShift = positionChangeYShift;
    myDialogMode = dialogMode;
    myTitle = title;
    myLayer = layer != null ? layer : Layer.normal;

    if (!myDialogMode) {
      new AwtVisitor(content) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof JLabel) {
            JLabel label = (JLabel)component;
            if (label.getDisplayedMnemonic() != '\0' || label.getDisplayedMnemonicIndex() >= 0) {
              myDialogMode = true;
              return true;
            }
          } else if (component instanceof JCheckBox) {
            JCheckBox checkBox = (JCheckBox)component;
            if (checkBox.getMnemonic() >= 0 || checkBox.getDisplayedMnemonicIndex() >= 0) {
              myDialogMode = true;
              return true;
            }
          }
          return false;
        }
      };
    }

    myShadow = shadow;
    myContainerInsets = contentInsets;

    myFadeoutTime = fadeoutTime;
    myAnimationCycle = animationCycle;

    if (smallVariant) {
      new AwtVisitor(myContent) {
        @Override
        public boolean visit(Component component) {
          UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, component);
          return false;
        }
      };
    }
  }

  public void show(final RelativePoint target, final Balloon.Position position) {
    AbstractPosition pos = getAbstractPositionFor(position);

    show(target, pos);
  }

  public int getLayer() {
    Integer result = JLayeredPane.DEFAULT_LAYER;
    switch (myLayer) {
      case normal:
        result = JLayeredPane.DEFAULT_LAYER;
        break;
      case top:
        result = JLayeredPane.DRAG_LAYER;
        break;
    }

    return result;
  }

  private static AbstractPosition getAbstractPositionFor(Position position) {
    AbstractPosition pos = BELOW;
    switch (position) {
      case atLeft:
        pos = AT_LEFT;
        break;
      case atRight:
        pos = AT_RIGHT;
        break;
      case below:
        pos = BELOW;
        break;
      case above:
        pos = ABOVE;
        break;
    }
    return pos;
  }

  public void show(PositionTracker<Balloon> tracker, Balloon.Position position) {
    AbstractPosition pos = BELOW;
    switch (position) {
      case atLeft:
        pos = AT_LEFT;
        break;
      case atRight:
        pos = AT_RIGHT;
        break;
      case below:
        pos = BELOW;
        break;
      case above:
        pos = ABOVE;
        break;
    }

    show(tracker, pos);
  }


  private void show(RelativePoint target, AbstractPosition position) {
    show(new PositionTracker.Static<Balloon>(target), position);
  }

  private void show(PositionTracker<Balloon> tracker, AbstractPosition position) {
    assert !myDisposed : "Balloon is already disposed";

    if (isVisible()) return;
    if (!tracker.getComponent().isShowing()) return;

    myTracker = tracker;
    myTracker.init(this);

    AbstractPosition originalPreferred = position;

    JRootPane root = null;
    JDialog dialog = IJSwingUtilities.findParentOfType(tracker.getComponent(), JDialog.class);
    if (dialog != null) {
      root = dialog.getRootPane();
    } else {
      JWindow jwindow = IJSwingUtilities.findParentOfType(tracker.getComponent(), JWindow.class);
      if (jwindow != null) {
        root = jwindow.getRootPane();
      } else {
        JFrame frame = IJSwingUtilities.findParentOfType(tracker.getComponent(), JFrame.class);
        if (frame != null) {
          root = frame.getRootPane();
        } else {
          assert false;
        }
      }
    }

    myVisible = true;

    myLayeredPane = root.getLayeredPane();
    myPosition = position;
    UIUtil.setFutureRootPane(myContent, root);

    myFocusManager = IdeFocusManager.findInstanceByComponent(myLayeredPane);
    final Ref<Component> originalFocusOwner = new Ref<Component>();
    final Ref<FocusRequestor> focusRequestor = new Ref<FocusRequestor>();
    final Ref<ActionCallback> proxyFocusRequest = new Ref<ActionCallback>(new ActionCallback.Done());

    boolean mnemonicsFix = myDialogMode && SystemInfo.isMac && Registry.is("ide.mac.inplaceDialogMnemonicsFix");
    if (mnemonicsFix) {
      final IdeGlassPaneEx glassPane = (IdeGlassPaneEx)IdeGlassPaneUtil.find(myLayeredPane);
      assert glassPane != null;

      proxyFocusRequest.set(new ActionCallback());

      myFocusManager.doWhenFocusSettlesDown(new ExpirableRunnable() {
        @Override
        public boolean isExpired() {
          return isDisposed();
        }

        @Override
        public void run() {
          IdeEventQueue.getInstance().disableInputMethods(BalloonImpl.this);
          originalFocusOwner.set(myFocusManager.getFocusOwner());
          myFocusManager.requestFocus(glassPane.getProxyComponent(), true).notify(proxyFocusRequest.get());
          focusRequestor.set(myFocusManager.getFurtherRequestor());
        }
      });
    }

    myLayeredPane.addComponentListener(myComponentListener);

    myTargetPoint = myPosition.getShiftedPoint(myTracker.recalculateLocation(this).getPoint(myLayeredPane), myCalloutshift);

    int positionChangeFix = 0;
    if (myShowPointer) {
      Rectangle rec = getRecForPosition(myPosition, true);

      if (!myPosition.isOkToHavePointer(myTargetPoint, rec, getPointerLength(myPosition), getPointerWidth(myPosition), getArc(), getNormalInset())) {
        rec = getRecForPosition(myPosition, false);

        Rectangle lp = new Rectangle(new Point(myContainerInsets.left, myContainerInsets.top), myLayeredPane.getSize());
        lp.width -= myContainerInsets.right;
        lp.height -= myContainerInsets.bottom;

        if (!lp.contains(rec)) {
          Rectangle2D currentSquare = lp.createIntersection(rec);

          double maxSquare = currentSquare.getWidth() * currentSquare.getHeight();
          AbstractPosition targetPosition = myPosition;

          for (AbstractPosition eachPosition : myPosition.getOtherPositions()) {
            Rectangle2D eachIntersection = lp.createIntersection(getRecForPosition(eachPosition, false));
            double eachSquare = eachIntersection.getWidth() * eachIntersection.getHeight();
            if (maxSquare < eachSquare) {
              maxSquare = eachSquare;
              targetPosition = eachPosition;
            }
          }

          myPosition = targetPosition;
          positionChangeFix = myPosition.getChangeShift(originalPreferred, myPositionChangeXShift, myPositionChangeYShift);
        }
      }
    }

    if (myPosition != originalPreferred) {
      myTargetPoint = myPosition.getShiftedPoint(myTracker.recalculateLocation(this).getPoint(myLayeredPane), myCalloutshift > 0 ? myCalloutshift + positionChangeFix : positionChangeFix);
    }

    createComponent();

    myComp.validate();

    Rectangle rec = myComp.getContentBounds();

    if (myShowPointer && !myPosition.isOkToHavePointer(myTargetPoint, rec, getPointerLength(myPosition), getPointerWidth(myPosition), getArc(), getNormalInset())) {
      myShowPointer = false;
      myComp.removeAll();
      myLayeredPane.remove(myComp);

      myForcedBounds = rec;
      createComponent();
    }

    for (JBPopupListener each : myListeners) {
      each.beforeShown(new LightweightWindowEvent(this));
    }

    runAnimation(true, myLayeredPane, null);

    myLayeredPane.revalidate();
    myLayeredPane.repaint();



    if (mnemonicsFix) {
      proxyFocusRequest.get().doWhenDone(new Runnable() {
        @Override
        public void run() {
          myFocusManager.requestFocus(originalFocusOwner.get(), true);
        }
      });
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(myAwtActivityListener, MouseEvent.MOUSE_EVENT_MASK |
                                                                           MouseEvent.MOUSE_MOTION_EVENT_MASK |
                                                                           KeyEvent.KEY_EVENT_MASK);
    if (ApplicationManager.getApplication() != null) {
      ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
                                                        @Override
                                                        public void beforeActionPerformed(AnAction action,
                                                                                          DataContext dataContext,
                                                                                          AnActionEvent event) {
                                                          if (myHideOnAction) {
                                                            hide();
                                                          }
                                                        }
                                                      }, this);
    }
  }

  private Rectangle getRecForPosition(AbstractPosition position, boolean adjust) {
    Dimension size = getContentSizeFor(position);

    Rectangle rec = new Rectangle(new Point(0, 0), size);

    position.setRecToRelativePosition(rec, myTargetPoint);

    if (adjust) {
      rec = myPosition
        .getUpdatedBounds(myLayeredPane.getSize(), myForcedBounds, rec.getSize(), myShowPointer, myTargetPoint, myContainerInsets, myCalloutshift);
    }

    return rec;
  }

  private Dimension getContentSizeFor(AbstractPosition position) {
    Insets insets = position.createBorder(this).getBorderInsets();
    if (insets == null) {
      insets = new Insets(0, 0, 0, 0);
    }

    Dimension size = myContent.getPreferredSize();
    size.width += insets.left + insets.right;
    size.height += insets.top + insets.bottom;

    return size;
  }

  private void createComponent() {
    myComp = new MyComponent(myContent, this, myShowPointer
                               ? myPosition.createBorder(this)
                               : getPointlessBorder());


    myComp.clear();
    myComp.myAlpha = 0f;

    final int borderSize = getShadowBorderSize();
    myComp.setBorder(new EmptyBorder(borderSize, borderSize, borderSize, borderSize));

    myLayeredPane.add(myComp);
    myLayeredPane.setLayer(myComp, getLayer());
    myPosition.updateBounds(this);
  }


  private static EmptyBorder getPointlessBorder() {
    return new EmptyBorder(getNormalInset(), getNormalInset(), getNormalInset(), getNormalInset());
  }

  public void revalidate() {
    revalidate(myTracker);
  }

  public void revalidate(PositionTracker<Balloon> tracker) {
    RelativePoint newPosition = tracker.recalculateLocation(this);

    if (newPosition != null) {
      myTargetPoint = myPosition.getShiftedPoint(newPosition.getPoint(myLayeredPane), myCalloutshift);
      myPosition.updateBounds(this);
    }
  }

  private int getShadowBorderSize() {
    return myShadow && Registry.is("ide.balloon.shadowEnabled") ? Registry.intValue("ide.balloon.shadow.size") : 0;
  }

  public void show(JLayeredPane pane) {
    show(pane, null);
  }

  @Override
  public void showInCenterOf(JComponent component) {
    final Dimension size = component.getSize();
    show(new RelativePoint(component, new Point(size.width/2, size.height/2)), Balloon.Position.above);
  }

  public void show(JLayeredPane pane, @Nullable Rectangle bounds) {
    if (bounds != null) {
      myForcedBounds = bounds;
    }
    show(new RelativePoint(pane, new Point(0, 0)), Balloon.Position.above);
  }


  private void runAnimation(boolean forward, final JLayeredPane layeredPane, @Nullable final Runnable onDone) {
    if (myAnimator != null) {
      Disposer.dispose(myAnimator);
    }
    myAnimator = new Animator("Balloon", 8, myAnimationEnabled ? myAnimationCycle : 0, false, forward) {
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        if (myComp == null || myComp.getParent() == null) return;
        myComp.setAlpha(((float)frame) / ((float)totalFrames));
      }

      @Override
      protected void paintCycleEnd() {
        if (myComp == null || myComp.getParent() == null) return;

        if (isForward()) {
          myComp.clear();
          myComp.repaint();

          myFadedIn = true;

          startFadeoutTimer((int)BalloonImpl.this.myFadeoutTime);
        }
        else {
          layeredPane.remove(myComp);
          layeredPane.revalidate();
          layeredPane.repaint();
        }
        Disposer.dispose(this);
      }

      @Override
      public void dispose() {
        super.dispose();
        myAnimator = null;
        if (onDone != null) {
          onDone.run();
        }
      }
    };

    myAnimator.resume();
  }

  public void startFadeoutTimer(final int fadeoutTime) {
    if (fadeoutTime > 0) {
      new Alarm(this).addRequest(new Runnable() {
        public void run() {
          hide();
        }
      }, fadeoutTime, null);
    }
  }


  int getArc() {
    return myDialogMode ? 6 : 3;
  }

  int getPointerWidth(AbstractPosition position) {
    if (myDialogMode) {
      return position.isTopBottomPointer() ? 24 : 17;
    } else {
      return position.isTopBottomPointer() ? 14 : 11;
    }
  }

  public static int getNormalInset() {
    return 3;
  }

  int getPointerLength(AbstractPosition position) {
    return getPointerLength(position, myDialogMode);
  }

  static int getPointerLength(AbstractPosition position, boolean dialogMode) {
    if (dialogMode) {
      return position.isTopBottomPointer() ? 16 : 14;
    } else {
      return position.isTopBottomPointer() ? 10 : 8;
    }
  }

  public static int getPointerLength(Position position, boolean dialogMode) {
    return getPointerLength(getAbstractPositionFor(position), dialogMode);
  }


  public void hide() {
    if (myDisposed) return;

    hideAndDispose();
  }

  public void addListener(JBPopupListener listener) {
    myListeners.add(listener);
  }

  private void hideAndDispose() {
    if (myDisposed) return;

    myDisposed = true;


    final Runnable disposeRunnable = new Runnable() {
      public void run() {
        myFadedOut = true;

        for (JBPopupListener each : myListeners) {
          each.onClosed(new LightweightWindowEvent(BalloonImpl.this));
        }

        Disposer.dispose(BalloonImpl.this);
        onDisposed();
      }
    };

    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtActivityListener);
    if (myLayeredPane != null) {
      myLayeredPane.removeComponentListener(myComponentListener);
      runAnimation(false, myLayeredPane, new Runnable() {
        @Override
        public void run() {
          disposeRunnable.run();
        }
      });
    } else {
      disposeRunnable.run();
    }

    myVisible = false;
    myTracker = null;
  }

  public void dispose() {
    if (myDisposed) return;

    hideAndDispose();
  }

  protected void onDisposed() {

  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setHideOnClickOutside(boolean hideOnMouse) {
    myHideOnMouse = hideOnMouse;
  }

  public void setShowPointer(final boolean show) {
    myShowPointer = show;
  }

  public Icon getCloseButton() {
    return myCloseButton;
  }

  public void setBounds(Rectangle bounds) {
    myForcedBounds = bounds;
    if (myPosition != null) {
      myPosition.updateBounds(this);
    }
  }

  public Dimension getPreferredSize() {
    if (myComp != null) {
      return myComp.getPreferredSize();
    } else {
      if (myDefaultPrefSize == null) {
        final EmptyBorder border = getPointlessBorder();
        final MyComponent c = new MyComponent(myContent, this, border);
        myDefaultPrefSize = c.getPreferredSize();
      }
      return myDefaultPrefSize;
    }
  }

  public abstract static class AbstractPosition {

    abstract EmptyBorder createBorder(final BalloonImpl balloon);


    abstract void setRecToRelativePosition(Rectangle rec, Point targetPoint);

    abstract int getChangeShift(AbstractPosition original, int xShift, int yShift);

    public void updateBounds(final BalloonImpl balloon) {
      final Rectangle bounds =
        getUpdatedBounds(balloon.myLayeredPane.getSize(), balloon.myForcedBounds, balloon.myComp.getPreferredSize(), balloon.myShowPointer,
                         balloon.myTargetPoint, balloon.myContainerInsets, balloon.myCalloutshift);

      final Point point = getShiftedPoint(bounds.getLocation(), -balloon.getShadowBorderSize());
      bounds.setLocation(point);
      balloon.myComp._setBounds(bounds);
    }

    public Rectangle getUpdatedBounds(Dimension layeredPaneSize,
                                      Rectangle forcedBounds,
                                      Dimension preferredSize,
                                      boolean showPointer,
                                      Point point, Insets containerInsets, int calloutShift) {

      Rectangle bounds = forcedBounds;

      if (bounds == null) {
        Point location = showPointer
                         ? getLocation(layeredPaneSize, point, preferredSize)
                         : new Point(point.x - preferredSize.width / 2, point.y - preferredSize.height / 2);
        bounds = new Rectangle(location.x, location.y, preferredSize.width, preferredSize.height);

        ScreenUtil.moveToFit(bounds, new Rectangle(0, 0, layeredPaneSize.width, layeredPaneSize.height), containerInsets);
      }

      return bounds;
    }

    abstract Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize);

    void paintComponent(BalloonImpl balloon, final Rectangle bounds, final Graphics2D g, Point pointTarget, JComponent component) {
      final GraphicsConfig cfg = new GraphicsConfig(g);

      Shape shape;
      if (balloon.myShowPointer) {
        shape = getPointingShape(bounds, g, pointTarget, balloon);
      }
      else {
        shape = new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, balloon.getArc(), balloon.getArc());
      }

      g.setColor(balloon.myFillColor);
      g.fill(shape);
      g.setColor(balloon.myBorderColor);

      if (balloon.myTitleLabel != null) {
        Rectangle titleBounds = balloon.myTitleLabel.getBounds();

        final int shadow = balloon.getShadowBorderSize();
        Insets inset = getTitleInsets(balloon.getNormalInset() - 1 + shadow, balloon.getPointerLength(this) + 50 + shadow);

        titleBounds.x -= inset.left + 1;
        titleBounds.width += (inset.left + inset.right + 50);
        titleBounds.y -= inset.top + 1;
        titleBounds.height += (inset.top + inset.bottom + 1);

        Area area = new Area(shape);
        area.intersect(new Area(titleBounds));


        Color fgColor = UIManager.getColor("Label.foreground");
        fgColor = ColorUtil.toAlpha(fgColor, 140);
        g.setColor(fgColor);
        g.fill(area);

        g.setColor(balloon.myBorderColor);
        g.draw(area);

        //Rectangle titleBounds = balloon.myTitleLabel.getBounds();
        //titleBounds = SwingUtilities.convertRectangle(balloon.myTitleLabel.getParent(), titleBounds, component);
        //
        //g.setColor(balloon.myBorderColor);
        //int inset  = balloon.getNormalInset();
        //g.drawLine(titleBounds.x - inset, (int)titleBounds.getMaxY(), (int)titleBounds.getMaxX() + inset, (int)titleBounds.getMaxY());
      }

      g.draw(shape);
      cfg.restore();
    }

    protected abstract Insets getTitleInsets(int normalInset, int pointerLength);

    protected abstract Shape getPointingShape(final Rectangle bounds,
                                              final Graphics2D g,
                                              final Point pointTarget,
                                              final BalloonImpl balloon);

    public boolean isOkToHavePointer(Point targetPoint, Rectangle bounds, int pointerLength, int pointerWidth, int arc, int normalInset) {
      if (bounds.x < targetPoint.x && bounds.x + bounds.width > targetPoint.x && bounds.y < targetPoint.y && bounds.y + bounds.height < targetPoint.y) return false;

      Rectangle pointless = getPointlessContentRec(bounds, pointerLength);

      int size = getDistanceToTarget(pointless, targetPoint);
      if (size < pointerLength) return false;

      Range<Integer> balloonRange;
      Range<Integer> pointerRange;
      if (isTopBottomPointer()) {
        balloonRange = new Range<Integer>(bounds.x + arc, bounds.x + bounds.width - arc * 2);
        pointerRange = new Range<Integer>(targetPoint.x - pointerWidth / 2, targetPoint.x + pointerWidth / 2);
      } else {
        balloonRange = new Range<Integer>(bounds.y + arc, bounds.y + bounds.height - arc * 2);
        pointerRange = new Range<Integer>(targetPoint.y - pointerWidth / 2, targetPoint.y + pointerWidth / 2);
      }

      return balloonRange.isWithin(pointerRange.getFrom()) && balloonRange.isWithin(pointerRange.getTo());
    }

    protected abstract int getDistanceToTarget(Rectangle rectangle, Point targetPoint);

    protected boolean isTopBottomPointer() {
      return this instanceof Below || this instanceof Above;
    }

    protected abstract Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength);

    public Set<AbstractPosition> getOtherPositions() {
      HashSet<AbstractPosition> all = new HashSet<AbstractPosition>();
      all.add(BELOW);
      all.add(ABOVE);
      all.add(AT_RIGHT);
      all.add(AT_LEFT);

      all.remove(this);

      return all;
    }

    public abstract Point getShiftedPoint(Point targetPoint, int shift);
  }

  public static final AbstractPosition BELOW = new Below();
  public static final AbstractPosition ABOVE = new Above();
  public static final AbstractPosition AT_RIGHT = new AtRight();
  public static final AbstractPosition AT_LEFT = new AtLeft();


  private static class Below extends AbstractPosition {


    @Override
    public Point getShiftedPoint(Point targetPoint, int shift) {
      return new Point(targetPoint.x, targetPoint.y + shift);
    }

    @Override
    int getChangeShift(AbstractPosition original, int xShift, int yShift) {
      return original == ABOVE ? yShift : 0;
    }


    @Override
    protected int getDistanceToTarget(Rectangle rectangle, Point targetPoint) {
      return rectangle.y - targetPoint.y;
    }

    @Override
    protected Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength) {
      return new Rectangle(bounds.x, bounds.y + pointerLength, bounds.width, bounds.height - pointerLength);
    }

    EmptyBorder createBorder(final BalloonImpl balloon) {
      return new EmptyBorder(balloon.getPointerLength(this) + balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset());
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(new Point(targetPoint.x - rec.width / 2, targetPoint.y));
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(center.x, targetPoint.y);
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      return new Insets(pointerLength, normalInset, normalInset, normalInset);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final BalloonImpl balloon) {
      bounds.y += balloon.getPointerLength(this);
      bounds.height -= balloon.getPointerLength(this) - 1;
    }

    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.TOP);
      shaper.line(balloon.getPointerWidth(this) / 2, balloon.getPointerLength(this)).toRightCurve().roundRightDown().toBottomCurve().roundLeftDown()
        .toLeftCurve().roundLeftUp().toTopCurve().roundUpRight()
        .lineTo(pointTarget.x - balloon.getPointerWidth(this) / 2, shaper.getCurrent().y).lineTo(pointTarget.x, pointTarget.y);
      shaper.close();

      return shaper.getShape();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      bounds.y += balloon.getPointerLength(this);
      bounds.height += balloon.getPointerLength(this);
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }

  }

  private static class Above extends AbstractPosition {

    @Override
    public Point getShiftedPoint(Point targetPoint, int shift) {
      return new Point(targetPoint.x, targetPoint.y - shift);
    }

    @Override
    int getChangeShift(AbstractPosition original, int xShift, int yShift) {
      return original == BELOW ? -yShift : 0;
    }

    @Override
    protected int getDistanceToTarget(Rectangle rectangle, Point targetPoint) {
      return targetPoint.y - (int)rectangle.getMaxY();
    }

    @Override
    protected Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength) {
      return new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height - pointerLength);
    }

    EmptyBorder createBorder(final BalloonImpl balloon) {
      return new EmptyBorder(balloon.getNormalInset(),
                             balloon.getNormalInset(),
                             balloon.getPointerLength(this),
                             balloon.getNormalInset());
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x - rec.width / 2, targetPoint.y - rec.height);
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(center.x, targetPoint.y - balloonSize.height);
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      return new Insets(normalInset, normalInset, normalInset, normalInset);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final BalloonImpl balloon) {
      bounds.height -= balloon.getPointerLength(this) - 1;
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      bounds.y -= balloon.getPointerLength(this);
      bounds.height -= balloon.getPointerLength(this);
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.BOTTOM);
      shaper.line(-balloon.getPointerWidth(this) / 2, -balloon.getPointerLength(this) + 1);
      shaper.toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown().toBottomCurve().line(0, 2)
        .roundLeftDown().lineTo(pointTarget.x + balloon.getPointerWidth(this) / 2, shaper.getCurrent().y).lineTo(pointTarget.x, pointTarget.y)
        .close();


      return shaper.getShape();
    }
  }

  private static class AtRight extends AbstractPosition {

    @Override
    public Point getShiftedPoint(Point targetPoint, int shift) {
      return new Point(targetPoint.x + shift, targetPoint.y);
    }

    @Override
    int getChangeShift(AbstractPosition original, int xShift, int yShift) {
      return original == AT_LEFT ? xShift : 0;
    }

    @Override
    protected int getDistanceToTarget(Rectangle rectangle, Point targetPoint) {
      return rectangle.x - targetPoint.x;
    }

    @Override
    protected Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength) {
      return new Rectangle(bounds.x + pointerLength, bounds.y, bounds.width - pointerLength, bounds.height);
    }

    EmptyBorder createBorder(final BalloonImpl balloon) {
      return new EmptyBorder(balloon.getNormalInset(), balloon.getPointerLength(this) + balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset());
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x, targetPoint.y - rec.height / 2);
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(targetPoint.x, center.y);
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      return new Insets(normalInset, pointerLength, normalInset, normalInset);
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.LEFT);
      shaper.line(balloon.getPointerLength(this), -balloon.getPointerWidth(this) / 2).toTopCurve().roundUpRight().toRightCurve().roundRightDown()
        .toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp()
        .lineTo(shaper.getCurrent().x, pointTarget.y + balloon.getPointerWidth(this) / 2).lineTo(pointTarget.x, pointTarget.y).close();

      return shaper.getShape();
    }

    protected void convertBoundsToContent(final Rectangle bounds, final BalloonImpl balloon) {
      bounds.x += balloon.getPointerLength(this);
      bounds.width -= balloon.getPointerLength(this);
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      bounds.x += balloon.getPointerLength(this);
      bounds.width -= balloon.getPointerLength(this);
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }
  }

  private static class AtLeft extends AbstractPosition {

    @Override
    public Point getShiftedPoint(Point targetPoint, int shift) {
      return new Point(targetPoint.x - shift, targetPoint.y);
    }

    @Override
    int getChangeShift(AbstractPosition original, int xShift, int yShift) {
      return original == AT_RIGHT ? -xShift : 0;
    }


    @Override
    protected int getDistanceToTarget(Rectangle rectangle, Point targetPoint) {
      return targetPoint.x - (int)rectangle.getMaxX();
    }

    @Override
    protected Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength) {
      return new Rectangle(bounds.x, bounds.y, bounds.width - pointerLength, bounds.height);
    }

    EmptyBorder createBorder(final BalloonImpl balloon) {
      return new EmptyBorder(balloon.getNormalInset(), balloon.getNormalInset(), balloon.getNormalInset(), balloon.getPointerLength(this) + balloon.getNormalInset());
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x - rec.width, targetPoint.y - rec.height / 2);
    }

    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize) {
      final Point center = UIUtil.getCenterPoint(new Rectangle(targetPoint, new Dimension(0, 0)), balloonSize);
      return new Point(targetPoint.x - balloonSize.width, center.y);
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      return new Insets(normalInset, pointerLength, normalInset, normalInset);
    }

    protected void convertBoundsToContent(final Rectangle bounds, final BalloonImpl balloon) {
      bounds.width -= balloon.getPointerLength(this);
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingUtilities.RIGHT);
      shaper.line(-balloon.getPointerLength(this), balloon.getPointerWidth(this) / 2);
      shaper.toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown()
        .lineTo(shaper.getCurrent().x, pointTarget.y - balloon.getPointerWidth(this) / 2).lineTo(pointTarget.x, pointTarget.y).close();
      return shaper.getShape();
    }

    protected Shape getShape(final Rectangle bounds, final Graphics2D g, final Point pointTarget, final BalloonImpl balloon) {
      bounds.width -= balloon.getPointerLength(this);
      return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, balloon.getArc(), balloon.getArc());
    }
  }

  private class CloseButton extends NonOpaquePanel {

    private BaseButtonBehavior myButton;

    private CloseButton() {
      myButton = new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        protected void execute(MouseEvent e) {
          if (!myEnableCloseButton) return;
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              BalloonImpl.this.hide();
            }
          });
        }
      };

      if (!myEnableCloseButton) {
        setVisible(false);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (!myEnableCloseButton) return;

      if (getWidth() > 0 && myLastMoveWasInsideBalloon) {
        final boolean pressed = myButton.isPressedByMouse();
        getCloseButton().paintIcon(this, g, (pressed ? 1 : 0), (pressed ? 1 : 0));
      }
    }
  }

  private class MyComponent extends JPanel {

    private BufferedImage myImage;
    private float myAlpha;
    private final BalloonImpl myBalloon;

    private final JComponent myContent;
    private ShadowBorderPainter.Shadow myShadow;

    private MyComponent(JComponent content, BalloonImpl balloon, EmptyBorder shapeBorder) {
      setOpaque(false);
      setLayout(null);
      myBalloon = balloon;

      setFocusCycleRoot(true);
      putClientProperty(Balloon.KEY, BalloonImpl.this);

      myContent = new JPanel(new BorderLayout(2, 2));
      Wrapper contentWrapper = new Wrapper(content);
      if (myTitle != null) {
        myTitleLabel = new JLabel(myTitle, JLabel.CENTER);
        myTitleLabel.setForeground(UIManager.getColor("List.background"));
        myTitleLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
        myContent.add(myTitleLabel, BorderLayout.NORTH);
        contentWrapper.setBorder(new EmptyBorder(1, 1, 1, 1));
      }
      myContent.add(contentWrapper, BorderLayout.CENTER);
      myContent.setBorder(shapeBorder);
      myContent.setOpaque(false);

      add(myContent);

      disposeCloseButton();
      myCloseRec = new CloseButton();
    }

    public Rectangle getContentBounds() {
      final Rectangle bounds = super.getBounds();
      final int shadow = myBalloon.getShadowBorderSize();
      bounds.x += shadow;
      bounds.width -= shadow * 2;
      bounds.y += shadow;
      bounds.height -= shadow * 2;
      return bounds;
    }

    public void clear() {
      myImage = null;
      myAlpha = -1;
    }

    @Override
    public void doLayout() {
      Insets insets = getInsets();
      if (insets == null) {
        insets = new Insets(0, 0, 0, 0);
      }

      myContent.setBounds(insets.left, insets.top, getWidth() - insets.left - insets.right, getHeight() - insets.top - insets.bottom);
    }

    @Override
    public Dimension getPreferredSize() {
      return addInsets(myContent.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
      return addInsets(myContent.getMinimumSize());
    }

    private Dimension addInsets(Dimension size) {
      final Insets insets = getInsets();
      if (insets != null) {
        size.width += (insets.left + insets.right);
        size.height += (insets.top + insets.bottom);
      }

      return size;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      super.paintComponent(g);

      final Graphics2D g2d = (Graphics2D)g;

      Point pointTarget = SwingUtilities.convertPoint(myLayeredPane, myBalloon.myTargetPoint, this);

      Rectangle shapeBounds = myContent.getBounds();

      final int shadowSize = myBalloon.getShadowBorderSize();
      if (shadowSize > 0) {
        if (myShadow == null) {
          initComponentImage(pointTarget, shapeBounds);
          myShadow = ShadowBorderPainter.createShadow(myImage, 0, 0, false, shadowSize / 2);
        }
      }

      if (myImage == null && myAlpha != -1) {
        initComponentImage(pointTarget, shapeBounds);
      }

      if (myImage != null && myAlpha != -1) {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));

        g2d.drawImage(myImage, 0, 0, null);
      }
      else {
        if (myShadow != null) {
          g.drawImage(myShadow.getImage(), myShadow.getX(), myShadow.getY(), null);
        }
        myBalloon.myPosition.paintComponent(myBalloon, shapeBounds, (Graphics2D)g, pointTarget, this);
      }
    }

    private void initComponentImage(Point pointTarget, Rectangle shapeBounds) {
      if (myImage != null) return;

      myImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      myBalloon.myPosition.paintComponent(myBalloon, shapeBounds, (Graphics2D)myImage.getGraphics(), pointTarget, this);
    }


    @Override
    public void removeNotify() {
      super.removeNotify();


      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          disposeCloseButton();
        }
      });
    }

    private void disposeCloseButton() {
      if (myCloseRec != null && myCloseRec.getParent() != null) {
        Container parent = myCloseRec.getParent();
        parent.remove(myCloseRec);
        ((JComponent)parent).revalidate();
        parent.repaint();
      }
    }

    public void setAlpha(float alpha) {
      myAlpha = alpha;
      paintImmediately(0, 0, getWidth(), getHeight());
    }

    public void _setBounds(Rectangle bounds) {
      Rectangle currentBounds = getBounds();
      if (currentBounds.width != bounds.width || currentBounds.height != bounds.height) {
        invalidateShadowImage();
      }

      super.setBounds(bounds);
      if (myCloseRec.getParent() == null && getParent() != null) {
        myLayeredPane.add(myCloseRec);
        myLayeredPane.setLayer(myCloseRec, JLayeredPane.DRAG_LAYER);
      }

      if (isVisible() && myCloseRec.isVisible()) {
        Rectangle lpBounds = SwingUtilities.convertRectangle(getParent(), bounds, myLayeredPane);
        lpBounds = myPosition.getPointlessContentRec(lpBounds, myBalloon.getPointerLength(myPosition));

        int iconWidth = myBalloon.myCloseButton.getIconWidth();
        int iconHeight = myBalloon.myCloseButton.getIconHeight();
        Rectangle r = new Rectangle(lpBounds.x + lpBounds.width - iconWidth + (int)(iconWidth * 0.3), lpBounds.y - (int)(iconHeight * 0.3), iconWidth, iconHeight);

        r.y += getShadowBorderSize();
        r.x -= getShadowBorderSize();

        myCloseRec.setBounds(r);
        
      }
      
      if (isVisible()) {
        revalidate();
        repaint();
      }
    }

    private void invalidateShadowImage() {
      myImage = null;
      myShadow = null;
    }

    public void repaintButton() {
      myCloseRec.repaint();
    }
  }

  private static class Shaper {
    private final GeneralPath myPath = new GeneralPath();

    Rectangle myBounds;
    private final int myTargetSide;
    private final BalloonImpl myBalloon;

    public Shaper(BalloonImpl balloon, Rectangle bounds, Point targetPoint, int targetSide) {
      myBalloon = balloon;
      myBounds = bounds;
      myTargetSide = targetSide;
      start(targetPoint);
    }

    private void start(Point start) {
      myPath.moveTo(start.x, start.y);
    }

    public Shaper roundUpRight() {
      myPath.quadTo(getCurrent().x, getCurrent().y - myBalloon.getArc(), getCurrent().x + myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    public Shaper roundRightDown() {
      myPath.quadTo(getCurrent().x + myBalloon.getArc(), getCurrent().y, getCurrent().x + myBalloon.getArc(),
                    getCurrent().y + myBalloon.getArc());
      return this;
    }

    public Shaper roundLeftUp() {
      myPath.quadTo(getCurrent().x - myBalloon.getArc(), getCurrent().y, getCurrent().x - myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    public Shaper roundLeftDown() {
      myPath.quadTo(getCurrent().x, getCurrent().y + myBalloon.getArc(), getCurrent().x - myBalloon.getArc(),
                    getCurrent().y + myBalloon.getArc());
      return this;
    }

    public Point getCurrent() {
      return new Point((int)myPath.getCurrentPoint().getX(), (int)myPath.getCurrentPoint().getY());
    }

    public Shaper line(final int deltaX, final int deltaY) {
      myPath.lineTo(getCurrent().x + deltaX, getCurrent().y + deltaY);
      return this;
    }

    public Shaper lineTo(final int x, final int y) {
      myPath.lineTo(x, y);
      return this;
    }


    private int getTargetDelta(int effectiveSide) {
      return effectiveSide == myTargetSide ? myBalloon.getPointerLength(myBalloon.myPosition) : 0;
    }

    public Shaper toRightCurve() {
      myPath.lineTo((int)myBounds.getMaxX() - myBalloon.getArc() - getTargetDelta(SwingUtilities.RIGHT) - 1, getCurrent().y);
      return this;
    }

    public Shaper toBottomCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getMaxY() - myBalloon.getArc() - getTargetDelta(SwingUtilities.BOTTOM) - 1);
      return this;
    }

    public Shaper toLeftCurve() {
      myPath.lineTo((int)myBounds.getX() + myBalloon.getArc() + getTargetDelta(SwingUtilities.LEFT), getCurrent().y);
      return this;
    }

    public Shaper toTopCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getY() + myBalloon.getArc() + getTargetDelta(SwingUtilities.TOP));
      return this;
    }

    public void close() {
      myPath.closePath();
    }

    public Shape getShape() {
      return myPath;
    }
  }

  @Override
  public boolean wasFadedIn() {
    return myFadedIn;
  }

  @Override
  public boolean wasFadedOut() {
    return myFadedOut;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void setTitle(String title) {
    myTitleLabel.setText(title);
  }

  @Override
  public RelativePoint getShowingPoint() {
    Point p = myPosition.getShiftedPoint(myTargetPoint, myCalloutshift * -1);
    return new RelativePoint(myLayeredPane, p);
  }

  @Override
  public void setAnimationEnabled(boolean enabled) {
    myAnimationEnabled = enabled;
  }
}
