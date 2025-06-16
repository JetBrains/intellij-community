// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.application.Topics;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.RemoteDesktopService;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.PopupLocationTracker;
import com.intellij.ide.ui.ScreenAreaConsumer;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.internal.statistic.collectors.fus.ui.BalloonUsageCollector;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.impl.ShadowBorderPainter;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonListener;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.WeakFocusStackManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.jcef.HwFacadeJPanel;
import com.intellij.ui.jcef.HwFacadeNonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.intellij.util.ui.UIUtil.useSafely;

public final class BalloonImpl implements Balloon, IdeTooltip.Ui, ScreenAreaConsumer {
  private static final Logger LOG = Logger.getInstance(BalloonImpl.class);

  /**
   * This key is supposed to be used as client property of content component (with value Boolean.TRUE) to suppress shadow painting
   *  when a builder is being created indirectly and a client cannot call its methods
   */
  public static final Key<Boolean> FORCED_NO_SHADOW = Key.create("BALLOON_FORCED_NO_SHADOW");

  private static final JBValue DIALOG_ARC = new JBValue.Float(6);
  public static final JBValue ARC = new JBValue.UIInteger("ToolTip.arc", 4);
  private static final JBValue DIALOG_TOPBOTTOM_POINTER_WIDTH = new JBValue.Float(24);
  public static final JBValue DIALOG_POINTER_WIDTH = new JBValue.Float(17);
  private static final JBValue TOPBOTTOM_POINTER_WIDTH = new JBValue.Float(14);
  private static final JBValue POINTER_WIDTH = new JBValue.Float(11);
  private static final JBValue DIALOG_TOPBOTTOM_POINTER_LENGTH = new JBValue.Float(16);
  private static final JBValue DIALOG_POINTER_LENGTH = new JBValue.Float(14);
  private static final JBValue TOPBOTTOM_POINTER_LENGTH = new JBValue.Float(10);
  public static final JBValue POINTER_LENGTH = new JBValue.Float(8);
  private static final JBValue BORDER_STROKE_WIDTH = new JBValue.Float(1);

  private final Alarm myFadeoutAlarm = new Alarm(this);
  private long myFadeoutRequestMillis;
  private int myFadeoutRequestDelay;

  private boolean mySmartFadeout;
  private boolean isSmartFadeoutPaused;
  private int mySmartFadeoutDelay;

  private MyComponent component;
  private JLayeredPane layeredPane;
  private AbstractPosition myPosition;
  private Point myTargetPoint;
  private final boolean myHideOnFrameResize;
  private final boolean myHideOnLinkClick;
  private boolean myZeroPositionInLayer = true;

  private final Color myBorderColor;
  private final Insets myBorderInsets;
  private Color myFillColor;
  private Color myPointerColor;

  private final Insets myContainerInsets;

  private boolean myLastMoveWasInsideBalloon;

  private Rectangle myForcedBounds;

  private ActionProvider myActionProvider;
  private List<ActionButton> myActionButtons;
  private boolean invalidateShadow;
  /**
   * Id for feature usage statistics.
   */
  private String myId;

  private final AWTEventListener myAwtActivityListener = new AWTEventListener() {
    @Override
    public void eventDispatched(final AWTEvent e) {
      if (mySmartFadeoutDelay > 0) {
        startFadeoutTimer(mySmartFadeoutDelay);
        mySmartFadeoutDelay = 0;
      }

      final int id = e.getID();
      if (e instanceof MouseEvent me) {
        final boolean insideBalloon = isInsideBalloon(me);

        boolean forcedExit = id == MouseEvent.MOUSE_EXITED && me.getButton() != MouseEvent.NOBUTTON && !myBlockClicks;
        if (myHideOnMouse && (id == MouseEvent.MOUSE_PRESSED || forcedExit)) {
          if ((!insideBalloon || forcedExit) && !isWithinChildWindow(me)) {
            if (myHideListener == null) {
              hide();
              if (forcedExit) {
                int[] ids = {MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED};
                for (int id_ : ids) {
                  IdeEventQueue.getInstance()
                    .dispatchEvent(new MouseEvent(me.getComponent(), id_, me.getWhen(), me.getModifiers(), me.getX(), me
                      .getY(), me.getClickCount(), me.isPopupTrigger(), me.getButton()));
                }
              }
            }
            else if (myHideListener instanceof HideListenerWithMouse listener) {
              listener.run(me);
            }
            else {
              myHideListener.run();
            }
          }
          return;
        }

        if (myClickHandler != null && id == MouseEvent.MOUSE_CLICKED) {
          if (!(me.getComponent() instanceof CloseButton) && insideBalloon) {
            myClickHandler.actionPerformed(new ActionEvent(me, ActionEvent.ACTION_PERFORMED, "click", me.getModifiersEx()));
            if (myCloseOnClick) {
              hide();
              return;
            }
          }
        }

        if (myEnableButtons && id == MouseEvent.MOUSE_MOVED) {
          final boolean moveChanged = insideBalloon != myLastMoveWasInsideBalloon;
          myLastMoveWasInsideBalloon = insideBalloon;
          if (moveChanged) {
            if (insideBalloon && !myFadeoutAlarm.isEmpty()) { //Pause hiding timer when mouse is hover
              myFadeoutAlarm.cancelAllRequests();
              myFadeoutRequestDelay -= System.currentTimeMillis() - myFadeoutRequestMillis;
            }
            if (!insideBalloon && myFadeoutRequestDelay > 0) {
              startFadeoutTimer(myFadeoutRequestDelay);
            }
            component.repaintButton();
          }
        }

        if (myHideOnCloseClick && UIUtil.isCloseClick(me)) {
          if (isInsideBalloon(me)) {
            hide();
            me.consume();
          }
          return;
        }
      }

      if ((myHideOnKey || myHideListener != null) && e instanceof KeyEvent ke && id == KeyEvent.KEY_PRESSED) {
        if (myHideListener != null) {
          if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
            myHideListener.run();
          }
          return;
        }
        if (ke.getKeyCode() != KeyEvent.VK_SHIFT &&
            ke.getKeyCode() != KeyEvent.VK_CONTROL &&
            ke.getKeyCode() != KeyEvent.VK_ALT &&
            ke.getKeyCode() != KeyEvent.VK_META &&
            ke.getKeyCode() != KeyEvent.VK_WINDOWS) {
          boolean doHide = false;
          // Close the balloon is ESC is pressed inside the balloon
          if (ke.getKeyCode() == KeyEvent.VK_ESCAPE && SwingUtilities.isDescendingFrom(ke.getComponent(), component)) {
            doHide = true;
          }
          // Close the balloon if any key is pressed outside the balloon
          //noinspection ConstantConditions
          if (myHideOnKey && !SwingUtilities.isDescendingFrom(ke.getComponent(), component)) {
            doHide = true;
          }
          if (doHide) {
            hide();
          }
        }
      }
    }
  };

  private boolean isWithinChildWindow(@NotNull MouseEvent event) {
    Component owner = ComponentUtil.getWindow(myContent);
    if (owner != null) {
      Component child = ComponentUtil.getWindow(event.getComponent());
      if (child != owner) {
        for (; child != null; child = child.getParent()) {
          if (child == owner) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void setFillColor(Color fillColor) {
    myFillColor = fillColor;
  }

  public void setPointerColor(Color pointerColor) {
    myPointerColor = pointerColor;
    layeredPane.revalidate();
    layeredPane.repaint();
  }

  private final long myFadeoutTime;
  private Dimension myDefaultPrefSize;
  private final ActionListener myClickHandler;
  private final boolean myCloseOnClick;
  private final int myShadowSize;
  private ShadowBorderProvider myShadowBorderProvider;

  private final Collection<JBPopupListener> myListeners = new CopyOnWriteArraySet<>();
  private boolean myVisible;
  private PositionTracker<Balloon> myTracker;
  private final int myAnimationCycle;

  private boolean myFadedIn;
  private boolean myFadedOut;
  private final int myCalloutShift;

  private final int myPositionChangeXShift;
  private final int myPositionChangeYShift;
  private boolean myDialogMode;
  private IdeFocusManager myFocusManager;
  private @NlsContexts.PopupTitle String myTitle;
  private JLabel myTitleLabel;

  private boolean myAnimationEnabled = true;
  private final boolean myShadow;
  private final Layer myLayer;
  private boolean myBlockClicks;
  private RelativePoint myPrevMousePoint;

  private boolean isInsideBalloon(@NotNull MouseEvent me) {
    return isInside(new RelativePoint(me));
  }

  @Override
  public boolean isInside(@NotNull RelativePoint target) {
    if (component == null) return false;
    Component cmp = target.getOriginalComponent();

    if (!cmp.isShowing()) return true;
    if (cmp instanceof MenuElement) return false;
    if (myActionButtons != null && myActionButtons.contains(cmp)) {
      return true;
    }
    if (UIUtil.isDescendingFrom(cmp, component)) return true;
    if (component == null || !component.isShowing()) return false;
    Point point = target.getScreenPoint();
    SwingUtilities.convertPointFromScreen(point, component);
    return component.contains(point);
  }

  public boolean isMovingForward(@NotNull RelativePoint target) {
    try {
      if (component == null || !component.isShowing()) return false;
      if (myPrevMousePoint == null) return true;
      if (myPrevMousePoint.getComponent() != target.getComponent()) return false;
      Rectangle rectangleOnScreen = new Rectangle(component.getLocationOnScreen(), component.getSize());
      return ScreenUtil.isMovementTowards(myPrevMousePoint.getScreenPoint(), target.getScreenPoint(), rectangleOnScreen);
    }
    finally {
      myPrevMousePoint = target;
    }
  }

  private final ComponentAdapter myComponentListener = new ComponentAdapter() {
    @Override
    public void componentResized(final ComponentEvent e) {
      if (myHideOnFrameResize) {
        hide();
      }
    }
  };
  private Animator animator;
  private boolean myShowPointer;

  private boolean isDisposed;
  private final JComponent myContent;
  private boolean myHideOnMouse;
  private Runnable myHideListener;
  private final boolean myHideOnKey;
  private final boolean myHideOnAction;
  private final boolean myHideOnCloseClick;
  private final boolean myRequestFocus;
  private Component myOriginalFocusOwner;
  private final boolean myEnableButtons;
  private final Dimension myPointerSize;
  private boolean myPointerShiftedToStart;
  private final int myCornerToPointerDistance;
  private int myCornerRadius = -1;
  private int myClipY = -1;
  private boolean myTopClip;

  public BalloonImpl(@NotNull JComponent content,
                     @NotNull Color borderColor,
                     Insets borderInsets,
                     @NotNull Color fillColor,
                     boolean hideOnMouse,
                     boolean hideOnKey,
                     boolean hideOnAction,
                     boolean hideOnCloseClick,
                     boolean showPointer,
                     boolean enableButtons,
                     long fadeoutTime,
                     boolean hideOnFrameResize,
                     boolean hideOnLinkClick,
                     ActionListener clickHandler,
                     boolean closeOnClick,
                     int animationCycle,
                     int calloutShift,
                     int positionChangeXShift,
                     int positionChangeYShift,
                     boolean dialogMode,
                     @NlsContexts.PopupTitle String title,
                     Insets contentInsets,
                     boolean shadow,
                     boolean smallVariant,
                     boolean blockClicks,
                     Layer layer,
                     boolean requestFocus,
                     Dimension pointerSize,
                     int cornerToPointerDistance) {
    myBorderColor = borderColor;
    myBorderInsets = borderInsets != null ? borderInsets : JBInsets.create(5, 8);
    myFillColor = fillColor;
    myContent = content;
    myHideOnMouse = hideOnMouse;
    myHideOnKey = hideOnKey;
    myHideOnAction = hideOnAction;
    myHideOnCloseClick = hideOnCloseClick;
    myShowPointer = showPointer;
    myEnableButtons = enableButtons;
    myHideOnFrameResize = hideOnFrameResize;
    myHideOnLinkClick = hideOnLinkClick;
    myClickHandler = clickHandler;
    myCloseOnClick = closeOnClick;
    myCalloutShift = calloutShift;
    myPositionChangeXShift = positionChangeXShift;
    myPositionChangeYShift = positionChangeYShift;
    myDialogMode = dialogMode;
    myTitle = title;
    myLayer = layer != null ? layer : Layer.normal;
    myBlockClicks = blockClicks;
    myRequestFocus = requestFocus;
    MnemonicHelper.init(content);

    if (!myDialogMode) {
      for (Component component : UIUtil.uiTraverser(myContent)) {
        if (component instanceof JLabel label) {
          if (label.getDisplayedMnemonic() != '\0' || label.getDisplayedMnemonicIndex() >= 0) {
            myDialogMode = true;
            break;
          }
        }
        else if (component instanceof JCheckBox checkBox) {
          if (checkBox.getMnemonic() >= 0 || checkBox.getDisplayedMnemonicIndex() >= 0) {
            myDialogMode = true;
            break;
          }
        }
      }
    }

    myShadow = shadow;
    myShadowSize = Registry.intValue("ide.balloon.shadow.size");
    myContainerInsets = contentInsets;

    myFadeoutTime = fadeoutTime;
    myAnimationCycle = animationCycle;

    myPointerSize = pointerSize;
    myCornerToPointerDistance = cornerToPointerDistance;

    if (smallVariant) {
      for (Component component : UIUtil.uiTraverser(myContent)) {
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, component);
      }
    }
  }

  @Override
  public void show(final RelativePoint target, final Balloon.Position position) {
    show(target, getAbstractPositionFor(position));
  }

  public int getLayer() {
    return switch (myLayer) {
      case normal -> JLayeredPane.POPUP_LAYER;
      case top -> JLayeredPane.DRAG_LAYER;
    };
  }

  public static AbstractPosition getAbstractPositionFor(@NotNull Position position) {
    return switch (position) {
      case atLeft -> AT_LEFT;
      case atRight -> AT_RIGHT;
      case above -> ABOVE;
      case below -> BELOW;
    };
  }

  @Override
  public void show(PositionTracker<Balloon> tracker, Balloon.Position position) {
    show(tracker, getAbstractPositionFor(position));
  }

  private Insets getInsetsCopy() {
    return JBInsets.create(myBorderInsets);
  }

  private void show(RelativePoint target, AbstractPosition position) {
    show(new PositionTracker.Static<>(target), position);
  }

  private void show(PositionTracker<Balloon> tracker, AbstractPosition position) {
    assert !isDisposed : "Balloon is already disposed";

    if (isVisible()) return;
    final Component comp = tracker.getComponent();
    if (!comp.isShowing()) return;

    myTracker = tracker;
    myTracker.init(this);

    JRootPane root = Objects.requireNonNull(UIUtil.getRootPane(comp));

    myVisible = true;

    layeredPane = root.getLayeredPane();
    myPosition = position;
    UIUtil.setFutureRootPane(myContent, root);

    myFocusManager = IdeFocusManager.findInstanceByComponent(layeredPane);
    final Ref<Component> originalFocusOwner = new Ref<>();
    final Ref<ActionCallback> proxyFocusRequest = new Ref<>(ActionCallback.DONE);

    boolean mnemonicsFix = myDialogMode && SystemInfo.isMac && Registry.is("ide.mac.inplaceDialogMnemonicsFix");
    if (mnemonicsFix) {
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
        }
      });
    }
    layeredPane.addComponentListener(myComponentListener);

    myTargetPoint = myPosition.getShiftedPoint(myTracker.recalculateLocation(this).getPoint(layeredPane), myCalloutShift);
    if (isDisposed) return; //tracker may dispose the balloon

    int positionChangeFix = 0;
    if (myShowPointer) {
      Rectangle rec = getRecForPosition(myPosition, true);
      JBInsets.removeFrom(rec, getShadowBorderInsets());

      if (!myPosition.isOkToHavePointer(myTargetPoint, rec, getPointerLength(myPosition), getPointerWidth(myPosition), getArc())) {
        rec = getRecForPosition(myPosition, false);

        Rectangle lp = new Rectangle(new Point(myContainerInsets.left, myContainerInsets.top), layeredPane.getSize());
        lp.width -= myContainerInsets.right;
        lp.height -= myContainerInsets.bottom;

        if (!lp.contains(rec) || !PopupLocationTracker.canRectangleBeUsed(layeredPane, rec, this)) {
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
          positionChangeFix = myPosition.getChangeShift(position, myPositionChangeXShift, myPositionChangeYShift);
        }
      }
    }

    if (myPosition != position) {
      myTargetPoint = myPosition.getShiftedPoint(myTracker.recalculateLocation(this).getPoint(layeredPane),
                                                 myCalloutShift > 0 ? myCalloutShift + positionChangeFix : positionChangeFix);
      position = myPosition;
    }

    createComponent();
    Rectangle r = getRecForPosition(myPosition, false);
    Point location = r.getLocation();
    SwingUtilities.convertPointToScreen(location, layeredPane);
    r.setLocation(location);
    if (!PopupLocationTracker.canRectangleBeUsed(layeredPane, r, this)) {
      for (AbstractPosition eachPosition : myPosition.getOtherPositions()) {
        r = getRecForPosition(eachPosition, false);
        location = r.getLocation();
        SwingUtilities.convertPointToScreen(location, layeredPane);
        r.setLocation(location);
        if (PopupLocationTracker.canRectangleBeUsed(layeredPane, r, this)) {
          myPosition = eachPosition;
          positionChangeFix = myPosition.getChangeShift(position, myPositionChangeXShift, myPositionChangeYShift);
          myTargetPoint = myPosition.getShiftedPoint(myTracker.recalculateLocation(this).getPoint(layeredPane),
                                                     myCalloutShift > 0 ? myCalloutShift + positionChangeFix : positionChangeFix);
          myPosition.updateBounds(this);
          break;
        }
      }
    }

    component.validate();

    Rectangle rec = component.getContentBounds();

    if (myShowPointer &&
        !myPosition.isOkToHavePointer(myTargetPoint, rec, getPointerLength(myPosition), getPointerWidth(myPosition), getArc())) {
      myShowPointer = false;
      component.removeAll();
      layeredPane.remove(component);

      createComponent();
      Dimension availSpace = layeredPane.getSize();
      Dimension reqSpace = component.getSize();
      if (!new Rectangle(availSpace).contains(new Rectangle(reqSpace))) {
        // Balloon is bigger than window, don't show it at all.
        LOG.warn("Not enough space to show: " +
                 "required [" + reqSpace.width + " x " + reqSpace.height + "], " +
                 "available [" + availSpace.width + " x " + availSpace.height + "]");
        component.removeAll();
        layeredPane.remove(component);
        layeredPane = null;
        hide();
        return;
      }
    }

    for (JBPopupListener each : myListeners) {
      each.beforeShown(new LightweightWindowEvent(this));
    }

    if (isAnimationEnabled()) {
      runAnimation(true, layeredPane, null);
    }

    layeredPane.revalidate();
    layeredPane.repaint();

    if (myRequestFocus) {
      myFocusManager.doWhenFocusSettlesDown(new ExpirableRunnable() {
        @Override
        public boolean isExpired() {
          return isDisposed();
        }

        @Override
        public void run() {
          myOriginalFocusOwner = myFocusManager.getFocusOwner();

          // Set the accessible parent so that screen readers don't announce
          // a window context change -- the tooltip is "logically" hosted
          // inside the component (e.g. editor) it appears on top of.
          AccessibleContextUtil.setParent(myContent, myOriginalFocusOwner);

          // Set the focus to "myContent"
          myFocusManager.requestFocus(getContentToFocus(), true);
        }
      });
    }

    if (mnemonicsFix) {
      proxyFocusRequest.get().doWhenDone(() -> myFocusManager.requestFocus(originalFocusOwner.get(), true));
    }

    Toolkit.getDefaultToolkit().addAWTEventListener(
      myAwtActivityListener, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);

    if (ApplicationManager.getApplication() != null) {
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(AnActionListener.TOPIC, new AnActionListener() {
        @Override
        public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
          if (myHideOnAction && !HintManagerImpl.isActionToIgnore(action)) {
            hide();
          }
        }
      });
    }

    if (myHideOnLinkClick) {
      JEditorPane editorPane = UIUtil.uiTraverser(myContent).traverse().filter(JEditorPane.class).first();
      if (editorPane != null) {
        editorPane.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
            hide();
          }
        });
      }
    }
    if (myId != null) {
      BalloonUsageCollector.BALLOON_SHOWN.log(myId);
    }

    getBalloonListener().balloonShown(this);
  }

  private static BalloonListener getBalloonListener() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(BalloonListener.TOPIC);
  }

  public AbstractPosition getPosition() {
    return myPosition;
  }

  /**
   * Figure out the component to focus inside the {@link #myContent} field.
   */
  private @NotNull Component getContentToFocus() {
    Component focusComponent = myContent;
    FocusTraversalPolicy policy = myContent.getFocusTraversalPolicy();
    if (policy instanceof SortingFocusTraversalPolicy &&
        ((SortingFocusTraversalPolicy)policy).getImplicitDownCycleTraversal()) {
      focusComponent = policy.getDefaultComponent(myContent);
    }
    while (true) {
      // Setting focus to a JScrollPane is not very useful. Better setting focus to the
      // contained view. This is useful for Tooltip popups, for example.
      if (focusComponent instanceof JScrollPane) {
        JViewport viewport = ((JScrollPane)focusComponent).getViewport();
        if (viewport == null) {
          break;
        }
        Component child = viewport.getView();
        if (child == null) {
          break;
        }
        focusComponent = child;
        continue;
      }

      // Done if we can't find anything to dive into
      break;
    }
    return focusComponent;
  }

  /**
   * @return rectangle with shadow insets
   */
  private Rectangle getRecForPosition(AbstractPosition position, boolean adjust) {
    Dimension size = getContentSizeFor(position);
    Rectangle rec = new Rectangle(new Point(0, 0), size);

    position.setRecToRelativePosition(rec, myTargetPoint);

    if (adjust) {
      rec = myPosition.getUpdatedBounds(this, rec.getSize(), getShadowBorderInsets());
    }
    else {
      JBInsets.addTo(rec, getShadowBorderInsets());
    }

    return rec;
  }

  private Dimension getContentSizeFor(AbstractPosition position) {
    Dimension size = myContent.getPreferredSize();
    if (myShadowBorderProvider == null) {
      JBInsets.addTo(size, position.createBorder(this).getBorderInsets());
    }
    return size;
  }

  private void disposeButton(ActionButton button) {
    if (button != null && button.getParent() != null) {
      Container parent = button.getParent();
      parent.remove(button);
      //noinspection RedundantCast
      ((JComponent)parent).revalidate();
      parent.repaint();
    }
  }

  public JComponent getContent() {
    return myContent;
  }

  public JComponent getComponent() {
    return component;
  }

  private void createComponent() {
    component = new MyComponent(myContent, this, myShadowBorderProvider != null ? null :
                                              myShowPointer ? myPosition.createBorder(this) : getPointlessBorder());

    if (myActionProvider == null) {
      final Consumer<MouseEvent> listener = event -> {
        SwingUtilities.invokeLater(() -> hide());
      };

      myActionProvider = new ActionProvider() {
        private ActionButton myCloseButton;

        @Override
        public @NotNull List<ActionButton> createActions() {
          myCloseButton = new CloseButton(listener);
          return Collections.singletonList(myCloseButton);
        }

        @Override
        public void layout(@NotNull Rectangle lpBounds) {
          if (myCloseButton == null || !myCloseButton.isVisible()) {
            return;
          }

          Icon icon = getCloseButton();
          int iconWidth = icon.getIconWidth();
          int iconHeight = icon.getIconHeight();
          Insets borderInsets = getShadowBorderInsets();

          myCloseButton.setBounds(lpBounds.x + lpBounds.width - iconWidth - borderInsets.right - JBUIScale.scale(8),
                                  lpBounds.y + borderInsets.top + JBUIScale.scale(6), iconWidth, iconHeight);
        }
      };
    }

    component.clear();
    component.myAlpha = isAnimationEnabled() ? 0f : -1;

    createComponentBorder();

    layeredPane.add(component);
    if (myZeroPositionInLayer) {
      layeredPane.setLayer(component, getLayer(), 0); // the second balloon must be over the first one
    }
    myPosition.updateBounds(this);

    PopupLocationTracker.register(this);

    if (myBlockClicks) {
      component.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          e.consume();
        }

        @Override
        public void mousePressed(MouseEvent e) {
          e.consume();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          e.consume();
        }
      });
    }

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(LafManagerListener.TOPIC, source -> updateComponent());
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> updateComponent());
  }

  private void createComponentBorder() {
    if (component == null) return;
    component.setBorder(new EmptyBorder(getShadowBorderInsets()));
  }

  private void updateComponent() {
    createComponentBorder();
  }

  @Override
  public @NotNull Rectangle getConsumedScreenBounds() {
    Rectangle bounds = component.getBounds();
    Point location = bounds.getLocation();
    SwingUtilities.convertPointToScreen(location, layeredPane);
    bounds.setLocation(location);
    return bounds;
  }

  @Override
  public Window getUnderlyingWindow() {
    return ComponentUtil.getWindow(layeredPane);
  }

  private @NotNull EmptyBorder getPointlessBorder() {
    return new EmptyBorder(myBorderInsets);
  }

  @Override
  public void revalidate() {
    if (!isDisposed && myTracker != null) {
      revalidate(myTracker);
    }
  }

  @Override
  public void revalidate(@NotNull PositionTracker<Balloon> tracker) {
    if (isDisposed || ApplicationManager.getApplication().isDisposed()) {
      return;
    }

    RelativePoint newPosition = tracker.recalculateLocation(this);
    if (newPosition != null) {
      Point newPoint = myPosition.getShiftedPoint(newPosition.getPoint(layeredPane), myCalloutShift);
      invalidateShadow = !Objects.equals(myTargetPoint, newPoint);
      myTargetPoint = newPoint;
      myPosition.updateBounds(this);
    }
  }

  public @Nullable ShadowBorderProvider getShadowBorderProvider() {
    return myShadowBorderProvider;
  }

  public void setShadowBorderProvider(@NotNull ShadowBorderProvider provider) {
    myShadowBorderProvider = provider;
  }

  private int getShadowBorderSize() {
    return hasShadow() ? myShadowSize : 0;
  }

  public @NotNull Insets getShadowBorderInsets() {
    if (myShadowBorderProvider != null) {
      return myShadowBorderProvider.getInsets();
    }
    return JBUI.insets(getShadowBorderSize());
  }

  public boolean hasShadow() {
    return myShadowBorderProvider != null || myShadow && Registry.is("ide.balloon.shadowEnabled");
  }

  public interface ShadowBorderProvider {
    @NotNull
    Insets getInsets();

    void paintShadow(@NotNull JComponent component, @NotNull Graphics g);

    void paintBorder(@NotNull Rectangle bounds, @NotNull Graphics2D g);

    void paintPointingShape(@NotNull Rectangle bounds, @NotNull Point pointTarget, @NotNull Position position, @NotNull Graphics2D g);
  }

  @Override
  public void show(JLayeredPane pane) {
    show(pane, null);
  }

  @Override
  public void showInCenterOf(JComponent component) {
    final Dimension size = component.getSize();
    show(new RelativePoint(component, new Point(size.width / 2, size.height / 2)), Balloon.Position.above);
  }

  public void show(JLayeredPane pane, @Nullable Rectangle bounds) {
    if (bounds != null) {
      myForcedBounds = bounds;
    }
    show(new RelativePoint(pane, new Point(0, 0)), Balloon.Position.above);
  }


  private void runAnimation(boolean forward, final JLayeredPane layeredPane, final @Nullable Runnable onDone) {
    if (animator != null) {
      animator.dispose();
    }

    animator = new Animator("Balloon", 8, isAnimationEnabled() ? myAnimationCycle : 0, false, forward) {
      @Override
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        if (component == null || component.getParent() == null || !isAnimationEnabled()) return;
        component.setAlpha((float)frame / totalFrames);
      }

      @Override
      protected void paintCycleEnd() {
        if (component == null || component.getParent() == null) return;

        if (isForward()) {
          component.clear();
          component.repaint();

          myFadedIn = true;

          if (!myFadeoutAlarm.isDisposed()) {
            startFadeoutTimer((int)myFadeoutTime);
          }
        }
        else {
          layeredPane.remove(component);
          layeredPane.revalidate();
          layeredPane.repaint();
        }
        dispose();
      }

      @Override
      public void dispose() {
        super.dispose();
        animator = null;
        if (onDone != null) {
          onDone.run();
        }
      }
    };

    animator.resume();
  }

  void runWithSmartFadeoutPause(@NotNull Runnable handler) {
    if (mySmartFadeout) {
      isSmartFadeoutPaused = true;
      handler.run();
      if (isSmartFadeoutPaused) {
        isSmartFadeoutPaused = false;
      }
      else {
        setAnimationEnabled(true);
        hide();
      }
    }
    else {
      handler.run();
    }
  }

  public void startSmartFadeoutTimer(int delay) {
    mySmartFadeout = true;
    mySmartFadeoutDelay = delay;
    Topics.subscribe(ApplicationActivationListener.TOPIC, this, new ApplicationActivationListener() {
      @Override
      public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
        if (!myFadeoutAlarm.isEmpty()) {
          myFadeoutAlarm.cancelAllRequests();
          mySmartFadeoutDelay = myFadeoutRequestDelay - (int)(System.currentTimeMillis() - myFadeoutRequestMillis);
          if (mySmartFadeoutDelay <= 0) {
            mySmartFadeoutDelay = 1;
          }
        }
      }
    });
  }

  public void startFadeoutTimer(final int fadeoutDelay) {
    if (fadeoutDelay > 0) {
      myFadeoutAlarm.cancelAllRequests();
      myFadeoutRequestMillis = System.currentTimeMillis();
      myFadeoutRequestDelay = fadeoutDelay;
      myFadeoutAlarm.addRequest((ContextAwareRunnable)() -> {
        if (mySmartFadeout) {
          setAnimationEnabled(true);
        }
        hide(true);
      }, fadeoutDelay, null);
    }
  }

  public void setCornerRadius(int radius) {
    myCornerRadius = radius;
  }

  private int getArc() {
    if (myCornerRadius != -1) {
      return myCornerRadius;
    }
    return myDialogMode ? DIALOG_ARC.get() : ARC.get();
  }

  private int getPointerWidth(AbstractPosition position) {
    if (myPointerSize == null || myPointerSize.width <= 0) {
      if (myDialogMode) {
        return position.isTopBottomPointer() ? DIALOG_TOPBOTTOM_POINTER_WIDTH.get() : DIALOG_POINTER_WIDTH.get();
      }
      else {
        return position.isTopBottomPointer() ? TOPBOTTOM_POINTER_WIDTH.get() : POINTER_WIDTH.get();
      }
    }
    else {
      return myPointerSize.width;
    }
  }

  public static int getNormalInset() {
    return 3;
  }

  private int getPointerLength(AbstractPosition position) {
    return myPointerSize == null || myPointerSize.height <= 0 ? getPointerLength(position, myDialogMode) : myPointerSize.height;
  }

  private static int getPointerLength(AbstractPosition position, boolean dialogMode) {
    if (dialogMode) {
      return position.isTopBottomPointer() ? DIALOG_TOPBOTTOM_POINTER_LENGTH.get() : DIALOG_POINTER_LENGTH.get();
    }
    else {
      return position.isTopBottomPointer() ? TOPBOTTOM_POINTER_LENGTH.get() : POINTER_LENGTH.get();
    }
  }

  public static int getPointerLength(@NotNull Position position, boolean dialogMode) {
    return getPointerLength(getAbstractPositionFor(position), dialogMode);
  }

  @Override
  public void hide() {
    hide(false);
  }

  @Override
  public void hide(boolean ok) {
    hideAndDispose(ok, false);
  }

  @Override
  public void dispose() {
    hideAndDispose(false, false);
  }

  @Override
  public void hideImmediately() {
    setAnimationEnabled(false);
    hideAndDispose(false, true);
  }

  private void hideAndDispose(boolean ok, boolean force) {
    if (isDisposed) {
      if (force) {
        disposeAnimationAndRemoveComponent();
      }
      return;
    }

    if (isSmartFadeoutPaused) {
      isSmartFadeoutPaused = false;
      return;
    }

    isDisposed = true;
    hideComboBoxPopups();

    Runnable disposeRunnable = () -> {
      myFadedOut = true;
      if (myRequestFocus) {
        if (myOriginalFocusOwner != null) {
          myFocusManager.requestFocus(myOriginalFocusOwner, false);
        }
      }

      for (JBPopupListener each : myListeners) {
        each.onClosed(new LightweightWindowEvent(this, ok));
      }

      Disposer.dispose(this);
      onDisposed();
    };

    Toolkit.getDefaultToolkit().removeAWTEventListener(myAwtActivityListener);
    if (layeredPane == null) {
      disposeRunnable.run();
    }
    else {
      layeredPane.removeComponentListener(myComponentListener);

      if (isAnimationEnabled()) {
        runAnimation(false, layeredPane, disposeRunnable);
      }
      else {
        disposeAnimationAndRemoveComponent();
        disposeRunnable.run();
      }
    }

    myVisible = false;
    myTracker = null;
  }

  private void disposeAnimationAndRemoveComponent() {
    if (animator != null) {
      animator.dispose();
    }
    if (component != null) {
      layeredPane.remove(component);
      layeredPane.revalidate();
      layeredPane.repaint();
      component = null;
    }
  }

  private void hideComboBoxPopups() {
    for (JComboBox<?> box : ComponentUtil.findComponentsOfType(component, JComboBox.class)) {
      box.hidePopup();
    }
  }

  private void onDisposed() {
  }

  @Override
  public void addListener(@NotNull JBPopupListener listener) {
    myListeners.add(listener);
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setHideOnClickOutside(boolean hideOnMouse) {
    myHideOnMouse = hideOnMouse;
  }

  public void setHideListener(@NotNull Runnable listener) {
    myHideListener = listener;
    myHideOnMouse = true;
  }

  public void setZeroPositionInLayer(boolean zeroPositionInLayer) {
    myZeroPositionInLayer = zeroPositionInLayer;
  }

  public void setShowPointer(final boolean show) {
    myShowPointer = show;
  }

  public void setPointerShiftedToStart(boolean pointerShiftedToStart) {
    myPointerShiftedToStart = pointerShiftedToStart;
  }

  public Icon getCloseButton() {
    return AllIcons.Ide.Notification.Close;
  }

  @Override
  public void setBounds(Rectangle bounds) {
    myForcedBounds = bounds;
    if (myPosition != null) {
      myPosition.updateBounds(this);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (component != null) {
      return component.getPreferredSize();
    }
    if (myDefaultPrefSize == null) {
      final EmptyBorder border = myShadowBorderProvider == null ? getPointlessBorder() : null;
      final MyComponent c = new MyComponent(myContent, this, border);

      c.setBorder(new EmptyBorder(getShadowBorderInsets()));
      myDefaultPrefSize = c.getPreferredSize();
    }
    return myDefaultPrefSize;
  }

  @ApiStatus.Internal
  public abstract static class AbstractPosition {
    abstract EmptyBorder createBorder(final BalloonImpl balloon);

    abstract void setRecToRelativePosition(Rectangle rec, Point targetPoint);

    abstract int getChangeShift(AbstractPosition original, int xShift, int yShift);

    public void updateBounds(final @NotNull BalloonImpl balloon) {
      if (balloon.layeredPane == null || balloon.component == null) return;

      Insets shadow = balloon.component.getInsets();
      Dimension prefSize = balloon.component.getPreferredSize();
      JBInsets.removeFrom(prefSize, shadow);
      Rectangle bounds = getUpdatedBounds(balloon, prefSize, shadow);

      if (balloon.myShadowBorderProvider == null && balloon.myForcedBounds != null) {
        bounds = new Rectangle(getShiftedPoint(bounds.getLocation(), balloon.getShadowBorderInsets()), bounds.getSize());
      }
      balloon.component._setBounds(bounds);
    }

    /**
     * @param contentSize size without shadow insets
     * @return adjusted size with shadow insets
     */
    @NotNull
    Rectangle getUpdatedBounds(BalloonImpl balloon, Dimension contentSize, Insets shadowInsets) {
      Dimension layeredPaneSize = balloon.layeredPane.getSize();
      Point point = balloon.myTargetPoint;

      Rectangle bounds = balloon.myForcedBounds;
      if (bounds == null) {
        int distance = getDistance(balloon, contentSize);
        Point location = balloon.myShowPointer
                         ? getLocation(layeredPaneSize, point, contentSize, distance)
                         // Now distance is used for pointer enabled balloons only
                         : new Point(point.x - contentSize.width / 2, point.y - contentSize.height / 2);
        if (balloon.myShowPointer && balloon.myPointerShiftedToStart) {
          int offset = JBUI.scale(20);
          if (this == ABOVE || this == BELOW) {
            if (contentSize.width / 2 > offset) {
              location.x = point.x - offset;
            }
          }
          else if (this == AT_LEFT || this == AT_RIGHT) {
            if (contentSize.height / 2 > offset) {
              location.y = point.y - offset;
            }
          }
        }
        bounds = new Rectangle(location.x, location.y, contentSize.width, contentSize.height);

        JBInsets.addTo(bounds, shadowInsets);
        ScreenUtil.moveToFit(bounds, new Rectangle(0, 0, layeredPaneSize.width, layeredPaneSize.height), balloon.myContainerInsets, true);
      }

      return bounds;
    }

    private int getDistance(@NotNull BalloonImpl balloon, @NotNull Dimension size) {
      if (balloon.myCornerToPointerDistance < 0) return -1;

      int indent = balloon.getArc() + balloon.getPointerWidth(this) / 2;
      if (balloon.myCornerToPointerDistance < indent) return indent;

      int limit = this == ABOVE || this == BELOW ? size.width - indent : size.height - indent;
      return Math.min(balloon.myCornerToPointerDistance, limit);
    }

    abstract Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize, int distance);

    void paintComponent(BalloonImpl balloon, final Rectangle bounds, final Graphics2D g, Point pointTarget) {
      final GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      if (balloon.myShadowBorderProvider != null) {
        balloon.myShadowBorderProvider.paintBorder(bounds, g);
        if (balloon.myShowPointer) {
          Position position;
          if (this == ABOVE) {
            position = Position.above;
          }
          else if (this == BELOW) {
            position = Position.below;
          }
          else if (this == AT_LEFT) {
            position = Position.atLeft;
          }
          else {
            position = Position.atRight;
          }
          balloon.myShadowBorderProvider.paintPointingShape(bounds, pointTarget, position, g);
        }
        cfg.restore();
        return;
      }

      Shape shape;
      if (balloon.myShowPointer) {
        shape = getPointingShape(bounds, pointTarget, balloon);
      }
      else {
        shape = getPointlessShape(balloon, bounds);
      }

      g.setPaint(balloon.myFillColor);
      g.fill(shape);
      if (balloon.myShowPointer && balloon.myPointerColor != null) {
        Shape balloonShape = getPointlessContentRec(bounds, getPointerLength(this, balloon.myDialogMode) + 1);
        Area area = new Area(shape);
        area.subtract(new Area(balloonShape));
        g.setColor(balloon.myPointerColor);
        g.fill(area);
      }
      g.setColor(balloon.myBorderColor);

      if (balloon.myTitleLabel != null) {
        Rectangle titleBounds = balloon.myTitleLabel.getBounds();

        Insets inset = getTitleInsets(getNormalInset() - 1, balloon.getPointerLength(this) + 50);
        Insets borderInsets = balloon.getShadowBorderInsets();
        inset.top += borderInsets.top;
        inset.bottom += borderInsets.bottom;
        inset.left += borderInsets.left;
        inset.right += borderInsets.right;

        titleBounds.x -= inset.left + JBUIScale.scale(1);
        titleBounds.width += inset.left + inset.right + JBUIScale.scale(50);
        titleBounds.y -= inset.top + JBUIScale.scale(1);
        titleBounds.height += inset.top + inset.bottom + JBUIScale.scale(1);

        Area area = new Area(shape);
        area.intersect(new Area(titleBounds));


        Color fgColor = UIManager.getColor("Label.foreground");
        fgColor = ColorUtil.toAlpha(fgColor, 140);
        g.setColor(fgColor);
        g.fill(area);

        g.setColor(balloon.myBorderColor);
        g.draw(area);
      }

      g.setStroke(new BasicStroke(BORDER_STROKE_WIDTH.get()));
      g.draw(shape);
      cfg.restore();
    }


    protected abstract Insets getTitleInsets(int normalInset, int pointerLength);

    protected abstract Shape getPointingShape(final Rectangle bounds,
                                              final Point pointTarget,
                                              final BalloonImpl balloon);

    /**
     * @param bounds rectangle without shadow insets
     */
    boolean isOkToHavePointer(@NotNull Point targetPoint, @NotNull Rectangle bounds, int pointerLength, int pointerWidth, int arc) {
      if (bounds.x < targetPoint.x &&
          bounds.x + bounds.width > targetPoint.x &&
          bounds.y < targetPoint.y &&
          bounds.y + bounds.height > targetPoint.y) {
        return false;
      }

      Rectangle pointless = getPointlessContentRec(bounds, pointerLength);

      int distance = getDistanceToTarget(pointless, targetPoint);
      if (distance < pointerLength - 1 || distance > 2 * pointerLength) return false;

      UnfairTextRange balloonRange;
      UnfairTextRange pointerRange;
      if (isTopBottomPointer()) {
        balloonRange = new UnfairTextRange(bounds.x + arc - 1, bounds.x + bounds.width - arc + 1);
        pointerRange = new UnfairTextRange(targetPoint.x - pointerWidth / 2, targetPoint.x + pointerWidth / 2);
      }
      else {
        balloonRange = new UnfairTextRange(bounds.y + arc - 1, bounds.y + bounds.height - arc + 1);
        pointerRange = new UnfairTextRange(targetPoint.y - pointerWidth / 2, targetPoint.y + pointerWidth / 2);
      }
      return balloonRange.contains(pointerRange);
    }

    protected abstract int getDistanceToTarget(Rectangle rectangle, Point targetPoint);

    boolean isTopBottomPointer() {
      return this instanceof Below || this instanceof Above;
    }

    protected abstract Rectangle getPointlessContentRec(Rectangle bounds, int pointerLength);

    @NotNull
    Set<AbstractPosition> getOtherPositions() {
      LinkedHashSet<AbstractPosition> all = new LinkedHashSet<>();
      all.add(BELOW);
      all.add(ABOVE);
      all.add(AT_RIGHT);
      all.add(AT_LEFT);

      all.remove(this);

      return all;
    }

    public abstract @NotNull Point getShiftedPoint(@NotNull Point targetPoint, int shift);

    public abstract @NotNull Point getShiftedPoint(@NotNull Point targetPoint, @NotNull Insets shift);

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  private static @NotNull RoundRectangle2D.Double getPointlessShape(BalloonImpl balloon, Rectangle bounds) {
    return new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width - JBUIScale.scale(1), bounds.height - JBUIScale.scale(1),
                                       balloon.getArc(), balloon.getArc());
  }

  static final AbstractPosition BELOW = new Below();
  static final AbstractPosition ABOVE = new Above();
  static final AbstractPosition AT_RIGHT = new AtRight();
  static final AbstractPosition AT_LEFT = new AtLeft();

  private static final class Below extends AbstractPosition {
    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, int shift) {
      return new Point(targetPoint.x, targetPoint.y + shift);
    }

    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, @NotNull Insets shift) {
      return getShiftedPoint(targetPoint, -shift.top);
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

    @Override
    EmptyBorder createBorder(final BalloonImpl balloon) {
      Insets insets = balloon.getInsetsCopy();
      insets.top += balloon.getPointerLength(this);
      return new EmptyBorder(insets);
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(new Point(targetPoint.x - rec.width / 2, targetPoint.y));
    }

    @Override
    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize, int distance) {
      if (distance > 0) {
        return new Point(targetPoint.x - distance, targetPoint.y);
      }
      else {
        final Point center = StartupUiUtil.getCenterPoint(new Rectangle(targetPoint, JBUI.emptySize()), balloonSize);
        return new Point(center.x, targetPoint.y);
      }
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      //noinspection UseDPIAwareInsets
      return new Insets(pointerLength, JBUIScale.scale(normalInset), JBUIScale.scale(normalInset), JBUIScale.scale(normalInset));
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, Point pointTarget, final BalloonImpl balloon) {
      pointTarget = new Point(pointTarget.x, Math.min(bounds.y, pointTarget.y));
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingConstants.TOP);
      shaper.line(balloon.getPointerWidth(this) / 2, balloon.getPointerLength(this)).toRightCurve().roundRightDown().toBottomCurve()
        .roundLeftDown()
        .toLeftCurve().roundLeftUp().toTopCurve().roundUpRight()
        .lineTo(pointTarget.x - balloon.getPointerWidth(this) / 2, shaper.getCurrent().y).lineTo(pointTarget.x, pointTarget.y);
      shaper.close();

      return shaper.getShape();
    }
  }

  private static final class Above extends AbstractPosition {
    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, int shift) {
      return new Point(targetPoint.x, targetPoint.y - shift);
    }

    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, @NotNull Insets shift) {
      return getShiftedPoint(targetPoint, -shift.top);
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

    @Override
    EmptyBorder createBorder(final BalloonImpl balloon) {
      Insets insets = balloon.getInsetsCopy();
      insets.bottom += balloon.getPointerLength(this);
      return new EmptyBorder(insets);
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x - rec.width / 2, targetPoint.y - rec.height);
    }

    @Override
    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize, int distance) {
      if (distance > 0) {
        return new Point(targetPoint.x - distance, targetPoint.y - balloonSize.height);
      }
      else {
        final Point center = StartupUiUtil.getCenterPoint(new Rectangle(targetPoint, JBUI.emptySize()), balloonSize);
        return new Point(center.x, targetPoint.y - balloonSize.height);
      }
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      return JBUI.insets(normalInset, normalInset, normalInset, normalInset);
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, Point pointTarget, final BalloonImpl balloon) {
      pointTarget = new Point(pointTarget.x, Math.max((int)bounds.getMaxY(), pointTarget.y));
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingConstants.BOTTOM);
      shaper.line(-balloon.getPointerWidth(this) / 2, -balloon.getPointerLength(this) + JBUIScale.scale(1));
      shaper.toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown().toBottomCurve().line(0, 2)
        .roundLeftDown().lineTo(pointTarget.x + balloon.getPointerWidth(this) / 2, shaper.getCurrent().y)
        .lineTo(pointTarget.x, pointTarget.y)
        .close();


      return shaper.getShape();
    }
  }

  private static final class AtRight extends AbstractPosition {
    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, int shift) {
      return new Point(targetPoint.x + shift, targetPoint.y);
    }

    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, @NotNull Insets shift) {
      return getShiftedPoint(targetPoint, -shift.left);
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

    @Override
    EmptyBorder createBorder(final BalloonImpl balloon) {
      Insets insets = balloon.getInsetsCopy();
      insets.left += balloon.getPointerLength(this);
      return new EmptyBorder(insets);
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x, targetPoint.y - rec.height / 2);
    }

    @Override
    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize, int distance) {
      if (distance > 0) {
        return new Point(targetPoint.x, targetPoint.y - distance);
      }
      else {
        final Point center = StartupUiUtil.getCenterPoint(new Rectangle(targetPoint, JBUI.emptySize()), balloonSize);
        return new Point(targetPoint.x, center.y);
      }
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      //noinspection UseDPIAwareInsets
      return new Insets(JBUIScale.scale(normalInset), pointerLength, JBUIScale.scale(normalInset), JBUIScale.scale(normalInset));
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, Point pointTarget, final BalloonImpl balloon) {
      pointTarget = new Point(Math.min(bounds.x, pointTarget.y), pointTarget.y);
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingConstants.LEFT);
      shaper.line(balloon.getPointerLength(this), -balloon.getPointerWidth(this) / 2).toTopCurve().roundUpRight().toRightCurve()
        .roundRightDown()
        .toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp()
        .lineTo(shaper.getCurrent().x, pointTarget.y + balloon.getPointerWidth(this) / 2).lineTo(pointTarget.x, pointTarget.y).close();

      return shaper.getShape();
    }
  }

  private static final class AtLeft extends AbstractPosition {
    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, int shift) {
      return new Point(targetPoint.x - shift, targetPoint.y);
    }

    @Override
    public @NotNull Point getShiftedPoint(@NotNull Point targetPoint, @NotNull Insets shift) {
      return getShiftedPoint(targetPoint, -shift.left);
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

    @Override
    EmptyBorder createBorder(final BalloonImpl balloon) {
      Insets insets = balloon.getInsetsCopy();
      insets.right += balloon.getPointerLength(this);
      return new EmptyBorder(insets);
    }

    @Override
    void setRecToRelativePosition(Rectangle rec, Point targetPoint) {
      rec.setLocation(targetPoint.x - rec.width, targetPoint.y - rec.height / 2);
    }

    @Override
    Point getLocation(final Dimension containerSize, final Point targetPoint, final Dimension balloonSize, int distance) {
      if (distance > 0) {
        return new Point(targetPoint.x - balloonSize.width, targetPoint.y - distance);
      }
      else {
        final Point center = StartupUiUtil.getCenterPoint(new Rectangle(targetPoint, JBUI.emptySize()), balloonSize);
        return new Point(targetPoint.x - balloonSize.width, center.y);
      }
    }

    @Override
    protected Insets getTitleInsets(int normalInset, int pointerLength) {
      //noinspection UseDPIAwareInsets
      return new Insets(JBUIScale.scale(normalInset), pointerLength, JBUIScale.scale(normalInset), JBUIScale.scale(normalInset));
    }

    @Override
    protected Shape getPointingShape(final Rectangle bounds, Point pointTarget, final BalloonImpl balloon) {
      pointTarget = new Point(Math.max((int)bounds.getMaxX(), pointTarget.x), pointTarget.y);
      final Shaper shaper = new Shaper(balloon, bounds, pointTarget, SwingConstants.RIGHT);
      shaper
        .lineTo((int)bounds.getMaxX() - shaper.getTargetDelta(SwingConstants.RIGHT) - JBUIScale.scale(1),
                pointTarget.y + balloon.getPointerWidth(this) / 2);
      shaper.toBottomCurve().roundLeftDown().toLeftCurve().roundLeftUp().toTopCurve().roundUpRight().toRightCurve().roundRightDown()
        .lineTo(shaper.getCurrent().x, pointTarget.y - balloon.getPointerWidth(this) / 2).lineTo(pointTarget.x, pointTarget.y).close();
      return shaper.getShape();
    }
  }

  public interface ActionProvider {
    @NotNull
    List<ActionButton> createActions();

    void layout(@NotNull Rectangle bounds);
  }

  public class ActionButton extends HwFacadeNonOpaquePanel implements IdeGlassPane.TopComponent {
    private final Icon myIcon;
    private final Icon myHoverIcon;
    private final Consumer<? super MouseEvent> myListener;
    protected final BaseButtonBehavior myButton;

    public ActionButton(@NotNull Icon icon,
                        @Nullable Icon hoverIcon,
                        @NlsContexts.Tooltip @Nullable String hint,
                        @NotNull Consumer<? super MouseEvent> listener) {
      myIcon = icon;
      myHoverIcon = hoverIcon;
      myListener = listener;

      setToolTipText(hint);

      myButton = new BaseButtonBehavior(this, TimedDeadzone.NULL, null) {
        @Override
        protected void execute(MouseEvent e) {
          myListener.consume(e);
        }
      };
      myButton.setupListeners();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(myIcon.getIconWidth(), myIcon.getIconHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (hasPaint()) {
        paintIcon(g, myHoverIcon != null && myButton.isHovered() ? myHoverIcon : myIcon);
      }
    }

    boolean hasPaint() {
      return getWidth() > 0 && myLastMoveWasInsideBalloon;
    }

    protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon) {
      icon.paintIcon(this, g, 0, 0);
    }

    @Override
    public boolean canBePreprocessed(@NotNull MouseEvent e) {
      return false;
    }
  }

  private final class CloseButton extends ActionButton {
    private CloseButton(@NotNull Consumer<? super MouseEvent> listener) {
      super(getCloseButton(), null, null, listener);
      setVisible(myEnableButtons);
    }

    @Override
    protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon) {
      if (myEnableButtons) {
        icon.paintIcon(this, g, 0, 0);
      }
    }
  }

  private final class MyComponent extends HwFacadeJPanel implements ComponentWithMnemonics {

    private BufferedImage myImage;
    private float myAlpha;
    private final BalloonImpl myBalloon;

    private final JComponent myContent;
    private ShadowBorderPainter.Shadow myShadow;

    @Override
    public void setVisible(boolean aFlag) {
      setActionButtonsVisible(aFlag);
      super.setVisible(aFlag);
    }

    private MyComponent(JComponent content, BalloonImpl balloon, EmptyBorder shapeBorder) {
      setOpaque(false);
      setLayout(null);
      putClientProperty(UIUtil.TEXT_COPY_ROOT, Boolean.TRUE);
      myBalloon = balloon;

      // When a screen reader is active, TAB/Shift-TAB should allow moving the focus
      // outside the balloon in the event the balloon acquired the focus.
      if (!ScreenReader.isActive()) {
        setFocusCycleRoot(true);
      }
      putClientProperty(Balloon.KEY, BalloonImpl.this);

      myContent = new JPanel(new BorderLayout(2, 2));
      Wrapper contentWrapper = new Wrapper(content);
      if (myTitle != null) {
        myTitleLabel = new JLabel(myTitle, SwingConstants.CENTER);
        myTitleLabel.setForeground(UIUtil.getListBackground());
        myTitleLabel.setBorder(JBUI.Borders.empty(0, 4));
        myContent.add(myTitleLabel, BorderLayout.NORTH);
        contentWrapper.setBorder(JBUI.Borders.empty(1));
      }
      myContent.add(contentWrapper, BorderLayout.CENTER);
      myContent.setBorder(shapeBorder);
      myContent.setOpaque(false);

      add(myContent);
      setFocusTraversalPolicyProvider(true);
      setFocusTraversalPolicy(new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
          return WeakFocusStackManager.getInstance().getLastFocusedOutside(MyComponent.this);
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
          return WeakFocusStackManager.getInstance().getLastFocusedOutside(MyComponent.this);
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
          return WeakFocusStackManager.getInstance().getLastFocusedOutside(MyComponent.this);
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          return WeakFocusStackManager.getInstance().getLastFocusedOutside(MyComponent.this);
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
          return WeakFocusStackManager.getInstance().getLastFocusedOutside(MyComponent.this);
        }
      });
    }

    @NotNull
    Rectangle getContentBounds() {
      Rectangle bounds = getBounds();
      JBInsets.removeFrom(bounds, getInsets());
      return bounds;
    }

    public void clear() {
      myImage = null;
      myAlpha = -1;
    }

    @Override
    public void doLayout() {
      Rectangle bounds = new Rectangle(getWidth(), getHeight());
      JBInsets.removeFrom(bounds, getInsets());

      myContent.setBounds(bounds);
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
      JBInsets.addTo(size, getInsets());
      return size;
    }

    @Override
    protected void paintChildren(Graphics g) {
      if (myImage == null || myAlpha == -1) {
        super.paintChildren(g);
      }
    }

    private void paintChildrenImpl(Graphics g) {
      // Paint to an image without alpha to preserve fonts subpixel antialiasing
      BufferedImage image = ImageUtil.createImage(g, getWidth(), getHeight(),
                                                  BufferedImage.TYPE_INT_RGB);//new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
      useSafely(image.createGraphics(), imageGraphics -> {
        //noinspection UseJBColor
        imageGraphics.setPaint(new Color(myFillColor.getRGB())); // create a copy to remove alpha
        imageGraphics.fillRect(0, 0, getWidth(), getHeight());

        super.paintChildren(imageGraphics);
      });

      Graphics2D g2d = (Graphics2D)g.create();
      try {
        if (JreHiDpiUtil.isJreHiDPI(g2d)) {
          float s = 1 / JBUIScale.sysScale(g2d);
          g2d.scale(s, s);
        }
        StartupUiUtil.drawImage(g2d, makeColorTransparent(image, myFillColor), 0, 0, null);
      }
      finally {
        g2d.dispose();
      }
    }

    private static Image makeColorTransparent(Image image, Color color) {
      final int markerRGB = color.getRGB() | 0xFF000000;
      ImageFilter filter = new RGBImageFilter() {
        @Override
        public int filterRGB(int x, int y, int rgb) {
          if ((rgb | 0xFF000000) == markerRGB) {
            return 0x00FFFFFF & rgb; // set alpha to 0
          }
          return rgb;
        }
      };
      return ImageUtil.filter(image, filter);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (myClipY != -1) {
        if (myTopClip) {
          //noinspection SSBasedInspection
          g.setClip(0, myClipY, getWidth(), getHeight() - myClipY);
        }
        else {
          //noinspection SSBasedInspection
          g.setClip(0, 0, getWidth(), myClipY);
        }
      }

      super.paintComponent(g);

      final Graphics2D g2d = (Graphics2D)g;

      Point pointTarget = SwingUtilities.convertPoint(layeredPane, myBalloon.myTargetPoint, this);
      Rectangle shapeBounds = myContent.getBounds();

      if (!DrawUtil.isSimplifiedUI()) {
        int shadowSize = myBalloon.getShadowBorderSize();

        if (shadowSize > 0 && myShadow == null && myShadowBorderProvider == null) {
          initComponentImage(pointTarget, shapeBounds);
          myShadow = ShadowBorderPainter.createShadow(myImage, 0, 0, false, shadowSize / 2);
        }

        if (myImage == null && myAlpha != -1) {
          initComponentImage(pointTarget, shapeBounds);
        }

        if (myImage != null && myAlpha != -1) {
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
        }

        if (myShadowBorderProvider != null) {
          myShadowBorderProvider.paintShadow(this, g);
        }
      }

      if (myImage != null && myAlpha != -1) {
        paintShadow(g);
        StartupUiUtil.drawImage(g2d, myImage, 0, 0, null);
      }
      else {
        paintShadow(g);
        myBalloon.myPosition.paintComponent(myBalloon, shapeBounds, (Graphics2D)g, pointTarget);
      }
    }

    private void paintShadow(Graphics graphics) {
      if (myShadow != null) {
        Graphics2D g2d = (Graphics2D)graphics;
        try {
          if (JreHiDpiUtil.isJreHiDPI(g2d)) {
            g2d = (Graphics2D)graphics.create();
            float s = 1 / JBUIScale.sysScale(this);
            g2d.scale(s, s);
          }
          StartupUiUtil.drawImage(g2d, myShadow.getImage(), myShadow.getX(), myShadow.getY(), null);
        }
        finally {
          if (g2d != graphics) g2d.dispose();
        }
      }
    }

    @Override
    public boolean contains(int x, int y) {
      Point pointTarget = SwingUtilities.convertPoint(layeredPane, myBalloon.myTargetPoint, this);
      Rectangle bounds = myContent.getBounds();
      Shape shape;
      if (myShowPointer) {
        shape = myBalloon.myPosition.getPointingShape(bounds, pointTarget, myBalloon);
      }
      else {
        shape = getPointlessShape(myBalloon, bounds);
      }
      return shape.contains(x, y);
    }

    private void initComponentImage(Point pointTarget, Rectangle shapeBounds) {
      if (myImage != null) return;

      myImage = UIUtil.createImage(component, getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      useSafely(myImage.getGraphics(), imageGraphics -> {
        myBalloon.myPosition.paintComponent(myBalloon, shapeBounds, imageGraphics, pointTarget);
        paintChildrenImpl(imageGraphics);
      });
    }


    @Override
    public void removeNotify() {
      super.removeNotify();

      if (!ScreenUtil.isStandardAddRemoveNotify(this)) {
        return;
      }

      final List<ActionButton> buttons = myActionButtons;
      myActionButtons = null;
      if (buttons != null) {
        SwingUtilities.invokeLater(() -> {
          for (ActionButton button : buttons) {
            disposeButton(button);
          }
        });
      }
    }

    public void setAlpha(float alpha) {
      myAlpha = alpha;
      paintImmediately(0, 0, getWidth(), getHeight());
    }

    void _setBounds(@NotNull Rectangle bounds) {
      Rectangle currentBounds = getBounds();
      if (!currentBounds.equals(bounds) || invalidateShadow) {
        invalidateShadowImage();
        invalidateShadow = false;
      }

      setBounds(bounds);
      doLayout();

      if (getParent() != null) {
        if (myActionButtons == null) {
          myActionButtons = myActionProvider.createActions();
        }

        for (ActionButton button : myActionButtons) {
          if (button.getParent() == null) {
            layeredPane.add(button);
            layeredPane.setLayer(button, JLayeredPane.DRAG_LAYER);
          }
        }
      }

      if (isVisible()) {
        Rectangle lpBounds = SwingUtilities.convertRectangle(getParent(), bounds, layeredPane);
        lpBounds = myPosition
          .getPointlessContentRec(lpBounds, myBalloon.myShadowBorderProvider == null ? myBalloon.getPointerLength(myPosition) : 0);
        myActionProvider.layout(lpBounds);
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

    void repaintButton() {
      if (myActionButtons != null) {
        for (ActionButton button : myActionButtons) {
          button.repaint();
        }
      }
    }
  }

  private static final class Shaper {
    private final GeneralPath myPath = new GeneralPath();

    Rectangle myBounds;
    @JdkConstants.TabPlacement
    private final int myTargetSide;
    private final BalloonImpl myBalloon;

    Shaper(BalloonImpl balloon, Rectangle bounds, Point targetPoint, @JdkConstants.TabPlacement int targetSide) {
      myBalloon = balloon;
      myBounds = bounds;
      myTargetSide = targetSide;
      start(targetPoint);
    }

    private void start(Point start) {
      myPath.moveTo(start.x, start.y);
    }

    @NotNull
    Shaper roundUpRight() {
      myPath.quadTo(getCurrent().x, getCurrent().y - myBalloon.getArc(), getCurrent().x + myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    @NotNull
    Shaper roundRightDown() {
      myPath.quadTo(getCurrent().x + myBalloon.getArc(), getCurrent().y, getCurrent().x + myBalloon.getArc(),
                    getCurrent().y + myBalloon.getArc());
      return this;
    }

    @NotNull
    Shaper roundLeftUp() {
      myPath.quadTo(getCurrent().x - myBalloon.getArc(), getCurrent().y, getCurrent().x - myBalloon.getArc(),
                    getCurrent().y - myBalloon.getArc());
      return this;
    }

    @NotNull
    Shaper roundLeftDown() {
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

    private int getTargetDelta(@JdkConstants.TabPlacement int effectiveSide) {
      return effectiveSide == myTargetSide ? myBalloon.getPointerLength(myBalloon.myPosition) : 0;
    }

    @NotNull
    Shaper toRightCurve() {
      myPath.lineTo((int)myBounds.getMaxX() - myBalloon.getArc() - getTargetDelta(SwingConstants.RIGHT) - JBUIScale.scale(1),
                    getCurrent().y);
      return this;
    }

    @NotNull
    Shaper toBottomCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getMaxY() - myBalloon.getArc() - getTargetDelta(SwingConstants.BOTTOM) -
                                    JBUIScale.scale(1));
      return this;
    }

    @NotNull
    Shaper toLeftCurve() {
      myPath.lineTo((int)myBounds.getX() + myBalloon.getArc() + getTargetDelta(SwingConstants.LEFT), getCurrent().y);
      return this;
    }

    @NotNull
    Shaper toTopCurve() {
      myPath.lineTo(getCurrent().x, (int)myBounds.getY() + myBalloon.getArc() + getTargetDelta(SwingConstants.TOP));
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
    return isDisposed;
  }

  public String getTitle() {
    return myTitle;
  }

  @Override
  public void setTitle(@NlsContexts.NotificationTitle String title) {
    myTitle = title;
    myTitleLabel.setText(title);
  }

  public void setActionProvider(@NotNull ActionProvider actionProvider) {
    myActionProvider = actionProvider;
  }

  @Override
  public RelativePoint getShowingPoint() {
    Point p = myPosition.getShiftedPoint(myTargetPoint, myCalloutShift * -1);
    return new RelativePoint(layeredPane, p);
  }

  @Override
  public void setAnimationEnabled(boolean enabled) {
    myAnimationEnabled = enabled;
  }

  public boolean isAnimationEnabled() {
    return myAnimationEnabled && myAnimationCycle > 0 && !RemoteDesktopService.isRemoteSession();
  }

  public void setBlockClicks(boolean blockClicks) {
    myBlockClicks = blockClicks;
  }

  public boolean isBlockClicks() {
    return myBlockClicks;
  }

  // Returns true if balloon is 'prepared' to process clicks by itself.
  // For example balloon would ignore clicks and won't hide explicitly or would trigger some actions/navigation
  public boolean isClickProcessor() {
    return myClickHandler != null || !myCloseOnClick || isBlockClicks();
  }

  public String getId() {
    return myId;
  }

  @Override
  public void setId(String id) {
    myId = id;
  }

  public int getClipY() {
    return myClipY;
  }

  public void setTopClip(boolean value) {
    myTopClip = value;
  }

  public void setClipY(int clipY) {
    int oldClip = myClipY;
    myClipY = clipY;
    if (oldClip != clipY) {
      component.repaint();
    }
  }

  public void setActionButtonsVisible(boolean aFlag) {
    if (myActionButtons != null) {
      for (ActionButton button : myActionButtons) {
        button.setVisible(aFlag);
      }
    }
  }

  public interface HideListenerWithMouse extends Runnable {
    void run(MouseEvent event);
  }
}
