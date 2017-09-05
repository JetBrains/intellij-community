/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.popup;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.ide.actions.WindowAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.Alarm;
import com.intellij.util.BooleanFunction;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.Processor;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public class AbstractPopup implements JBPopup {
  public static final String SHOW_HINTS = "ShowHints";

  // Popup size stored with DimensionService is null first time
  // In this case you can put Dimension in content client properties to adjust size
  // Zero or negative values (with/height or both) would be ignored (actual values would be obtained from preferred size)
  public static final String FIRST_TIME_SIZE = "FirstTimeSize";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.AbstractPopup");

  private static final Object SUPPRESS_MAC_CORNER = new Object();

  private PopupComponent myPopup;
  private MyContentPanel myContent;
  private JComponent     myPreferredFocusedComponent;
  private boolean        myRequestFocus;
  private boolean        myFocusable;
  private boolean        myForcedHeavyweight;
  private boolean        myLocateWithinScreen;
  private boolean myResizable = false;
  private WindowResizeListener myResizeListener;
  private WindowMoveListener myMoveListener;
  private JPanel myHeaderPanel;
  private CaptionPanel myCaption = null;
  private JComponent myComponent;
  private String              myDimensionServiceKey = null;
  private Computable<Boolean> myCallBack            = null;
  private Project              myProject;
  private boolean              myCancelOnClickOutside;
  private Set<JBPopupListener> myListeners;
  private boolean              myUseDimServiceForXYLocation;
  private MouseChecker         myCancelOnMouseOutCallback;
  private Canceller            myMouseOutCanceller;
  private boolean              myCancelOnWindow;
  private boolean myCancelOnWindowDeactivation = true;
  private   Dimension         myForcedSize;
  private   Point             myForcedLocation;
  private   boolean           myCancelKeyEnabled;
  private   boolean           myLocateByContent;
  protected FocusTrackback    myFocusTrackback;
  private   Dimension         myMinSize;
  private   List<Object>      myUserData;
  private   boolean           myShadowed;

  private float myAlpha     = 0;
  private float myLastAlpha = 0;

  private MaskProvider myMaskProvider;

  private Window           myWindow;
  private boolean          myInStack;
  private MyWindowListener myWindowListener;

  private boolean myModalContext;

  private   Component[] myFocusOwners;
  private   PopupBorder myPopupBorder;
  private   Dimension   myRestoreWindowSize;
  protected Component   myOwner;
  protected Component   myRequestorComponent;
  private   boolean     myHeaderAlwaysFocusable;
  private   boolean     myMovable;
  private   JComponent  myHeaderComponent;

  protected InputEvent myDisposeEvent;

  private Runnable myFinalRunnable;
  @Nullable private BooleanFunction<KeyEvent> myKeyEventHandler;

  protected boolean myOk;

  protected final SpeedSearch mySpeedSearch = new SpeedSearch() {
    boolean searchFieldShown = false;

    @Override
    public void update() {
      mySpeedSearchPatternField.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      onSpeedSearchPatternChanged();
      mySpeedSearchPatternField.setText(getFilter());
      if (isHoldingFilter() && !searchFieldShown) {
        setHeaderComponent(mySpeedSearchPatternField);
        searchFieldShown = true;
      }
      else if (!isHoldingFilter() && searchFieldShown) {
        setHeaderComponent(null);
        searchFieldShown = false;
      }
    }

    @Override
    public void noHits() {
      mySpeedSearchPatternField.getTextEditor().setBackground(LightColors.RED);
    }
  };

  protected SearchTextField mySpeedSearchPatternField;
  private boolean myNativePopup;
  private boolean myMayBeParent;
  private AbstractPopup.SpeedSearchKeyListener mySearchKeyListener;
  private JLabel myAdComponent;
  private boolean myDisposed;

  private UiActivity myActivityKey;
  private Disposable myProjectDisposable;

  private volatile State myState = State.NEW;

  private enum State {NEW, INIT, SHOWING, SHOWN, CANCEL, DISPOSE}

  private void debugState(String message, State... states) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(hashCode() + " - " + message);
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        LOG.debug("unexpected thread");
      }
      for (State state : states) {
        if (state == myState) {
          return;
        }
      }
      LOG.debug(new IllegalStateException("myState=" + myState));
    }
  }

  protected AbstractPopup() { }

  protected AbstractPopup init(Project project,
                     @NotNull JComponent component,
                     @Nullable JComponent preferredFocusedComponent,
                     boolean requestFocus,
                     boolean focusable,
                     boolean movable,
                     String dimensionServiceKey,
                     boolean resizable,
                     @Nullable String caption,
                     @Nullable Computable<Boolean> callback,
                     boolean cancelOnClickOutside,
                     @Nullable Set<JBPopupListener> listeners,
                     boolean useDimServiceForXYLocation,
                     ActiveComponent commandButton,
                     @Nullable IconButton cancelButton,
                     @Nullable MouseChecker cancelOnMouseOutCallback,
                     boolean cancelOnWindow,
                     @Nullable ActiveIcon titleIcon,
                     boolean cancelKeyEnabled,
                     boolean locateByContent,
                     boolean placeWithinScreenBounds,
                     @Nullable Dimension minSize,
                     float alpha,
                     @Nullable MaskProvider maskProvider,
                     boolean inStack,
                     boolean modalContext,
                     @Nullable Component[] focusOwners,
                     @Nullable String adText,
                     int adTextAlignment,
                     boolean headerAlwaysFocusable,
                     @NotNull List<Pair<ActionListener, KeyStroke>> keyboardActions,
                     Component settingsButtons,
                     @Nullable final Processor<JBPopup> pinCallback,
                     boolean mayBeParent,
                     boolean showShadow,
                     boolean showBorder,
                     Color borderColor,
                     boolean cancelOnWindowDeactivation,
                     @Nullable BooleanFunction<KeyEvent> keyEventHandler)
  {
    if (requestFocus && !focusable) {
      assert false : "Incorrect argument combination: requestFocus=true focusable=false";
    }

    myActivityKey = new UiActivity.Focus("Popup:" + this);
    myProject = project;
    myComponent = component;
    myPopupBorder = showBorder ? borderColor != null ? PopupBorder.Factory.createColored(borderColor) :
                                 PopupBorder.Factory.create(true, showShadow) :
                                 PopupBorder.Factory.createEmpty();
    myShadowed = showShadow;
    myContent = createContentPanel(resizable, myPopupBorder, isToDrawMacCorner() && resizable);
    myMayBeParent = mayBeParent;
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;

    myContent.add(component, BorderLayout.CENTER);
    if (adText != null) {
      setAdText(adText, adTextAlignment);
    }

    myCancelKeyEnabled = cancelKeyEnabled;
    myLocateByContent = locateByContent;
    myLocateWithinScreen = placeWithinScreenBounds;
    myAlpha = alpha;
    myMaskProvider = maskProvider;
    myInStack = inStack;
    myModalContext = modalContext;
    myFocusOwners = focusOwners;
    myHeaderAlwaysFocusable = headerAlwaysFocusable;
    myMovable = movable;

    ActiveIcon actualIcon = titleIcon == null ? new ActiveIcon(EmptyIcon.ICON_0) : titleIcon;

    myHeaderPanel = new JPanel(new BorderLayout());

    if (caption != null) {
      if (!caption.isEmpty()) {
        myCaption = new TitlePanel(actualIcon.getRegular(), actualIcon.getInactive());
        ((TitlePanel)myCaption).setText(caption);
      }
      else {
        myCaption = new CaptionPanel();
      }

      if (pinCallback != null) {
        myCaption.setButtonComponent(new InplaceButton(
          new IconButton("Open as Tool Window", 
                         AllIcons.General.AutohideOff, AllIcons.General.AutohideOff, AllIcons.General.AutohideOffInactive),
          e -> pinCallback.process(this)
        ), JBUI.Borders.empty(4));
      }
      else if (cancelButton != null) {
        myCaption.setButtonComponent(new InplaceButton(cancelButton, e -> cancel()), JBUI.Borders.empty(4));
      }
      else if (commandButton != null) {
        myCaption.setButtonComponent(commandButton, null);
      }
    }
    else {
      myCaption = new CaptionPanel();
      myCaption.setBorder(null);
      myCaption.setPreferredSize(JBUI.emptySize());
    }

    setWindowActive(myHeaderAlwaysFocusable);

    myHeaderPanel.add(myCaption, BorderLayout.NORTH);
    myContent.add(myHeaderPanel, BorderLayout.NORTH);

    myForcedHeavyweight = true;
    myResizable = resizable;
    myPreferredFocusedComponent = preferredFocusedComponent;
    myRequestFocus = requestFocus;
    myFocusable = focusable;
    myDimensionServiceKey = dimensionServiceKey;
    myCallBack = callback;
    myCancelOnClickOutside = cancelOnClickOutside;
    myCancelOnMouseOutCallback = cancelOnMouseOutCallback;
    myListeners = listeners == null ? new HashSet<>() : listeners;
    myUseDimServiceForXYLocation = useDimServiceForXYLocation;
    myCancelOnWindow = cancelOnWindow;
    myMinSize = minSize;

    for (Pair<ActionListener, KeyStroke> pair : keyboardActions) {
      myContent.registerKeyboardAction(pair.getFirst(), pair.getSecond(), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    if (settingsButtons != null) {
      myCaption.addSettingsComponent(settingsButtons);
    }

    myKeyEventHandler = keyEventHandler;
    debugState("popup initialized", State.NEW);
    myState = State.INIT;
    return this;
  }

  private void setWindowActive(boolean active) {
    boolean value = myHeaderAlwaysFocusable || active;

    if (myCaption != null) {
      myCaption.setActive(value);
    }
    myPopupBorder.setActive(value);
    myContent.repaint();
  }


  @NotNull
  protected MyContentPanel createContentPanel(final boolean resizable, PopupBorder border, boolean isToDrawMacCorner) {
    return new MyContentPanel(resizable, border, isToDrawMacCorner);
  }

  public boolean isToDrawMacCorner() {
    if (!SystemInfo.isMac || myComponent.getComponentCount() <= 0) {
      return false;
    }

    if (SystemInfo.isMacOSYosemite) {
      return false;
    }

    if (myComponent.getComponentCount() > 0) {
      Component component = myComponent.getComponent(0);
      if (component instanceof JComponent && Boolean.TRUE.equals(((JComponent)component).getClientProperty(SUPPRESS_MAC_CORNER))) {
        return false;
      }
    }

    return true;
  }

  public void setShowHints(boolean show) {
    final Window ancestor = getContentWindow(myComponent);
    if (ancestor instanceof RootPaneContainer) {
      final JRootPane rootPane = ((RootPaneContainer)ancestor).getRootPane();
      if (rootPane != null) {
        rootPane.putClientProperty(SHOW_HINTS, Boolean.valueOf(show));
      }
    }
  }

  public static void suppressMacCornerFor(JComponent popupComponent) {
    popupComponent.putClientProperty(SUPPRESS_MAC_CORNER, Boolean.TRUE);
  }


  public String getDimensionServiceKey() {
    return myDimensionServiceKey;
  }

  public void setDimensionServiceKey(@Nullable final String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
  }

  @Override
  public void showInCenterOf(@NotNull Component aContainer) {
    final Point popupPoint = getCenterOf(aContainer, getPreferredContentSize());
    show(aContainer, popupPoint.x, popupPoint.y, false);
  }

  public void setAdText(@NotNull final String s) {
    setAdText(s, SwingConstants.LEFT);
  }

  @NotNull
  public PopupBorder getPopupBorder() {
    return myPopupBorder;
  }

  @Override
  public void setAdText(@NotNull final String s, int alignment) {
    if (myAdComponent == null) {
      myAdComponent = HintUtil.createAdComponent(s, JBUI.Borders.empty(1, 5), alignment);
      JPanel wrapper = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          g.setColor(Gray._135);
          g.drawLine(0, 0, getWidth(), 0);
          super.paintComponent(g);
        }
      };
      wrapper.setOpaque(false);
      wrapper.setBorder(JBUI.Borders.emptyTop(1));
      wrapper.add(myAdComponent, BorderLayout.CENTER);
      myContent.add(wrapper, BorderLayout.SOUTH);
      pack(false, true);
    } else {
      myAdComponent.setText(s);
      myAdComponent.setHorizontalAlignment(alignment);
    }
  }

  public static Point getCenterOf(final Component aContainer, final JComponent content) {
    return getCenterOf(aContainer, content.getPreferredSize());
  }

  private static Point getCenterOf(final Component aContainer, final Dimension contentSize) {
    final JComponent component = getTargetComponent(aContainer);

    Rectangle visibleBounds = component != null
                              ? component.getVisibleRect()
                              : new Rectangle(aContainer.getSize());

    Point containerScreenPoint = visibleBounds.getLocation();
    SwingUtilities.convertPointToScreen(containerScreenPoint, aContainer);
    visibleBounds.setLocation(containerScreenPoint);
    return UIUtil.getCenterPoint(visibleBounds, contentSize);
  }

  @Override
  public void showCenteredInCurrentWindow(@NotNull Project project) {
    Window window = null;

    Component focusedComponent = getWndManager().getFocusedComponent(project);
    if (focusedComponent != null) {
      Component parent = UIUtil.findUltimateParent(focusedComponent);
      if (parent instanceof Window) {
        window = (Window)parent;
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    if (window != null && window.isShowing()) {
      showInCenterOf(window);
    }
  }

  @Override
  public void showUnderneathOf(@NotNull Component aComponent) {
    show(new RelativePoint(aComponent, UIUtil.isUnderWin10LookAndFeel() ?
              new Point(2, aComponent.getHeight()) :
              new Point(0, aComponent.getHeight())));
  }

  @Override
  public void show(@NotNull RelativePoint aPoint) {
    final Point screenPoint = aPoint.getScreenPoint();
    show(aPoint.getComponent(), screenPoint.x, screenPoint.y, false);
  }

  @Override
  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {
    show(owner, point.x, point.y, false);
  }

  @Override
  public void showInBestPositionFor(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null && editor.getComponent().isShowing()) {
      showInBestPositionFor(editor);
    }
    else {
      show(relativePointByQuickSearch(dataContext));
    }
  }

  @Override
  public void showInFocusCenter() {
    final Component focused = getWndManager().getFocusedComponent(myProject);
    if (focused != null) {
      showInCenterOf(focused);
    }
    else {
      final WindowManager manager = WindowManager.getInstance();
      final JFrame frame = myProject != null ? manager.getFrame(myProject) : manager.findVisibleFrame();
      showInCenterOf(frame.getRootPane());
    }
  }

  private RelativePoint relativePointByQuickSearch(final DataContext dataContext) {
    Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(dataContext);

    if (dominantArea != null) {
      final Component focusedComponent = getWndManager().getFocusedComponent(myProject);
      if (focusedComponent != null) {
        Window window = SwingUtilities.windowForComponent(focusedComponent);
        JLayeredPane layeredPane;
        if (window instanceof JFrame) {
          layeredPane = ((JFrame)window).getLayeredPane();
        }
        else if (window instanceof JDialog) {
          layeredPane = ((JDialog)window).getLayeredPane();
        }
        else if (window instanceof JWindow) {
          layeredPane = ((JWindow)window).getLayeredPane();
        }
        else {
          throw new IllegalStateException("cannot find parent window: project=" + myProject + "; window=" + window);
        }

        return relativePointWithDominantRectangle(layeredPane, dominantArea);
      }
    }

    return JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    assert editor.getComponent().isShowing() : "Editor must be showing on the screen";

    // Set the accessible parent so that screen readers don't announce
    // a window context change -- the tooltip is "logically" hosted
    // inside the component (e.g. editor) it appears on top of.
    AccessibleContextUtil.setParent(myComponent, editor.getContentComponent());
    DataContext context = ((EditorEx)editor).getDataContext();
    Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(context);
    if (dominantArea != null && !myRequestFocus) {
      final JLayeredPane layeredPane = editor.getContentComponent().getRootPane().getLayeredPane();
      show(relativePointWithDominantRectangle(layeredPane, dominantArea));
    }
    else {
      show(guessBestPopupLocation(editor));
    }
  }

  @NotNull
  private RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
    RelativePoint preferredLocation = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
    if (myDimensionServiceKey == null) {
      return preferredLocation;
    }
    Dimension preferredSize = DimensionService.getInstance().getSize(myDimensionServiceKey, myProject);
    if (preferredSize == null) {
      return preferredLocation;
    }
    Rectangle preferredBounds = new Rectangle(preferredLocation.getScreenPoint(), preferredSize);
    Rectangle adjustedBounds = new Rectangle(preferredBounds);
    ScreenUtil.moveRectangleToFitTheScreen(adjustedBounds);
    if (preferredBounds.y - adjustedBounds.y <= 0) {
      return preferredLocation;
    }
    int adjustedY = preferredBounds.y - editor.getLineHeight() - preferredSize.height;
    if (adjustedY < 0) {
      return preferredLocation;
    }
    Point point = new Point(preferredBounds.x, adjustedY);
    Component component = preferredLocation.getComponent();
    if (component == null) {
      return RelativePoint.fromScreen(point);
    }
    SwingUtilities.convertPointFromScreen(point, component);
    return new RelativePoint(component, point);
  }

  public void addPopupListener(JBPopupListener listener) {
    myListeners.add(listener);
  }

  private RelativePoint relativePointWithDominantRectangle(final JLayeredPane layeredPane, final Rectangle bounds) {
    Dimension preferredSize = getPreferredContentSize();
    final Point leftTopCorner = new Point(bounds.x + bounds.width, bounds.y);
    final Point leftTopCornerScreen = (Point)leftTopCorner.clone();
    SwingUtilities.convertPointToScreen(leftTopCornerScreen, layeredPane);
    final RelativePoint relativePoint;
    if (!ScreenUtil.isOutsideOnTheRightOFScreen(
      new Rectangle(leftTopCornerScreen.x, leftTopCornerScreen.y, preferredSize.width, preferredSize.height))) {
      relativePoint = new RelativePoint(layeredPane, leftTopCorner);
    }
    else {
      if (bounds.x > preferredSize.width) {
        relativePoint = new RelativePoint(layeredPane, new Point(bounds.x - preferredSize.width, bounds.y));
      }
      else {
        setDimensionServiceKey(null); // going to cut width
        Rectangle screen = ScreenUtil.getScreenRectangle(leftTopCornerScreen.x, leftTopCornerScreen.y);
        final int spaceOnTheLeft = bounds.x;
        final int spaceOnTheRight = screen.x + screen.width - leftTopCornerScreen.x;
        if (spaceOnTheLeft > spaceOnTheRight) {
          relativePoint = new RelativePoint(layeredPane, new Point(0, bounds.y));
          myComponent.setPreferredSize(new Dimension(spaceOnTheLeft, Math.max(preferredSize.height, JBUI.scale(200))));
        }
        else {
          relativePoint = new RelativePoint(layeredPane, leftTopCorner);
          myComponent.setPreferredSize(new Dimension(spaceOnTheRight, Math.max(preferredSize.height, JBUI.scale(200))));
        }
      }
    }
    return relativePoint;
  }

  private Dimension getPreferredContentSize() {
    if (myForcedSize != null) {
      return myForcedSize;
    }
    if (myDimensionServiceKey != null) {
      final Dimension dimension = DimensionService.getInstance().getSize(myDimensionServiceKey, myProject);
      if (dimension != null) return dimension;
    }
    return myComponent.getPreferredSize();
  }

  @Override
  public final void closeOk(@Nullable InputEvent e) {
    setOk(true);
    cancel(e);
  }

  @Override
  public final void cancel() {
    cancel(null);
  }

  @Override
  public void setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  @Override
  public void cancel(InputEvent e) {
    if (myState == State.CANCEL || myState == State.DISPOSE) {
      return;
    }
    debugState("cancel popup", State.SHOWN);
    myState = State.CANCEL;

    if (isDisposed()) return;

    if (myPopup != null) {
      if (!canClose()) {
        debugState("cannot cancel popup", State.CANCEL);
        myState = State.SHOWN;
        return;
      }
      storeDimensionSize(myContent.getSize());
      if (myUseDimServiceForXYLocation) {
        final JRootPane root = myComponent.getRootPane();
        if (root != null) {
          final Container popupWindow = root.getParent();
          if (popupWindow != null && popupWindow.isShowing()) {
            storeLocation(fixLocateByContent(popupWindow.getLocationOnScreen(), true));
          }
        }
      }

      if (e instanceof MouseEvent) {
        IdeEventQueue.getInstance().blockNextEvents((MouseEvent)e);
      }

      myPopup.hide(false);

      if (ApplicationManagerEx.getApplicationEx() != null) {
        StackingPopupDispatcher.getInstance().onPopupHidden(this);
      }

      if (myInStack) {
        if (myFocusTrackback != null) {
          myFocusTrackback.setForcedRestore(!myOk && myFocusable);
          myFocusTrackback.restoreFocus();
        }
        else if (LOG.isDebugEnabled()) {
          LOG.debug("cancel before show @ " + Thread.currentThread());
        }
      }


      disposePopup();

      if (myListeners != null) {
        for (JBPopupListener each : myListeners) {
          each.onClosed(new LightweightWindowEvent(this, myOk));
        }
      }
    }

    Disposer.dispose(this, false);
    if (myProjectDisposable != null) {
      Disposer.dispose(myProjectDisposable);
    }
  }

  public FocusTrackback getFocusTrackback() {
    return myFocusTrackback;
  }

  private void disposePopup() {
    if (myPopup != null) {
      resetWindow();
      myPopup.hide(true);
    }
    myPopup = null;
  }

  @Override
  public boolean canClose() {
    return myCallBack == null || myCallBack.compute().booleanValue();
  }

  @Override
  public boolean isVisible() {
    if (myPopup == null) return false;
    Window window = myPopup.getWindow();
    if (window != null && window.isShowing()) return true;
    if (LOG.isDebugEnabled()) LOG.debug("window hidden, popup's state: " + myState);
    return false;
  }

  @Override
  public void show(final Component owner) {
    show(owner, -1, -1, true);
  }

  public void show(Component owner, int aScreenX, int aScreenY, final boolean considerForcedXY) {
    if (ApplicationManagerEx.getApplicationEx() != null && ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (isDisposed()) {
      throw new IllegalStateException("Popup was already disposed. Recreate a new instance to show again");
    }

    assert ApplicationManager.getApplication().isDispatchThread();
    assert myState == State.INIT : "Popup was already shown. Recreate a new instance to show again.";

    debugState("show popup", State.INIT);
    myState = State.SHOWING;

    installWindowHook(this);
    installProjectDisposer();
    addActivity();

    final Component prevOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    final boolean shouldShow = beforeShow();
    if (!shouldShow) {
      removeActivity();
      debugState("rejected to show popup", State.SHOWING);
      myState = State.INIT;
      return;
    }

    prepareToShow();

    if (myInStack) {
      myFocusTrackback = new FocusTrackback(this, owner, true);
      myFocusTrackback.setMustBeShown(true);
    }


    Dimension sizeToSet = null;

    if (myDimensionServiceKey != null) {
      sizeToSet = DimensionService.getInstance().getSize(myDimensionServiceKey, myProject);
    }

    if (myForcedSize != null) {
      sizeToSet = myForcedSize;
    }

    Rectangle screen = ScreenUtil.getScreenRectangle(aScreenX, aScreenY);
    if (myLocateWithinScreen) {
      Dimension preferredSize = myContent.getPreferredSize();
      Object o = myContent.getClientProperty(FIRST_TIME_SIZE);
      if (sizeToSet == null && o instanceof Dimension) {
        int w = ((Dimension)o).width;
        int h = ((Dimension)o).height;
        if (w > 0) preferredSize.width = w;
        if (h > 0) preferredSize.height = h;
        sizeToSet = preferredSize;
      }
      Dimension size = sizeToSet != null ? sizeToSet : preferredSize;
      if (size.width > screen.width) {
        size.width = screen.width;
        sizeToSet = size;
      }
      if (size.height > screen.height) {
        size.height = screen.height;
        sizeToSet = size;
      }
    }

    if (sizeToSet != null) {
      sizeToSet.width = Math.max(sizeToSet.width, myContent.getMinimumSize().width);
      sizeToSet.height = Math.max(sizeToSet.height, myContent.getMinimumSize().height);

      myContent.setSize(sizeToSet);
      myContent.setPreferredSize(sizeToSet);
    }

    Point xy = new Point(aScreenX, aScreenY);
    boolean adjustXY = true;
    if (myUseDimServiceForXYLocation && myDimensionServiceKey != null) {
      final Point storedLocation = DimensionService.getInstance().getLocation(myDimensionServiceKey, myProject);
      if (storedLocation != null) {
        xy = storedLocation;
        adjustXY = false;
      }
    }

    if (adjustXY) {
      final Insets insets = myContent.getInsets();
      if (insets != null) {
        xy.x -= insets.left;
        xy.y -= insets.top;
      }
    }

    if (considerForcedXY && myForcedLocation != null) {
      xy = myForcedLocation;
    }

    fixLocateByContent(xy, false);

    Rectangle targetBounds = new Rectangle(xy, myContent.getPreferredSize());
    if (targetBounds.width > screen.width || targetBounds.height > screen.height) {
      LOG.warn("huge popup requested: " + targetBounds.width + " x " + targetBounds.height);
    }
    Rectangle original = new Rectangle(targetBounds);
    if (myLocateWithinScreen) {
      ScreenUtil.moveToFit(targetBounds, screen, null);
    }

    if (myMouseOutCanceller != null) {
      myMouseOutCanceller.myEverEntered = targetBounds.equals(original);
    }

    myOwner = getFrameOrDialog(owner); // use correct popup owner for non-modal dialogs too
    if (myOwner == null) {
      myOwner = owner;
    }

    myRequestorComponent = owner;

    boolean forcedDialog = myMayBeParent
      || SystemInfo.isMac && !(myOwner instanceof IdeFrame) && myOwner != null && myOwner.isShowing();

    PopupComponent.Factory factory = getFactory(myForcedHeavyweight || myResizable, forcedDialog);
    myNativePopup = factory.isNativePopup();
    Component popupOwner = myOwner;
    if (popupOwner instanceof RootPaneContainer && !(popupOwner instanceof IdeFrame && !Registry.is("popup.fix.ide.frame.owner"))) {
      // JDK uses cached heavyweight popup for a window ancestor
      RootPaneContainer root = (RootPaneContainer)popupOwner;
      popupOwner = root.getRootPane();
      LOG.debug("popup owner fixed for JDK cache");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expected preferred size: " + myContent.getPreferredSize());
    }
    myPopup = factory.getPopup(popupOwner, myContent, targetBounds.x, targetBounds.y, this);
    if (LOG.isDebugEnabled()) {
      LOG.debug("  actual preferred size: " + myContent.getPreferredSize());
    }
    if ((targetBounds.width != myContent.getWidth()) || (targetBounds.height != myContent.getHeight())) {
      // JDK uses cached heavyweight popup that is not initialized properly
      LOG.debug("the expected size is not equal to the actual size");
      Window popup = myPopup.getWindow();
      if (popup != null) {
        popup.setSize(targetBounds.width, targetBounds.height);
        if (myContent.getParent().getComponentCount() != 1) {
          LOG.debug("unexpected count of components in heavy-weight popup");
        }
      }
      else {
        LOG.debug("cannot fix size for non-heavy-weight popup");
      }
    }

    if (myResizable) {
      final JRootPane root = myContent.getRootPane();
      final IdeGlassPaneImpl glass = new IdeGlassPaneImpl(root);
      root.setGlassPane(glass);

      int i = Registry.intValue("ide.popup.resizable.border.sensitivity", 4);
      WindowResizeListener resizeListener = new WindowResizeListener(
        myContent,
        myMovable ? JBUI.insets(i, i, i, i) : JBUI.insets(0, 0, i, i),
        isToDrawMacCorner() ? AllIcons.General.MacCorner : null) {
        private Cursor myCursor;

        @Override
        protected void setCursor(Component content, Cursor cursor) {
          if (myCursor != cursor || myCursor != Cursor.getDefaultCursor()) {
            glass.setCursor(cursor, this);
            myCursor = cursor;
          }
        }
      };
      glass.addMousePreprocessor(resizeListener, this);
      glass.addMouseMotionPreprocessor(resizeListener, this);
      myResizeListener = resizeListener;
    }

    if (myCaption != null && myMovable) {
      final WindowMoveListener moveListener = new WindowMoveListener(myCaption) {
        @Override
        public void mousePressed(final MouseEvent e) {
          if (e.isConsumed()) return;
          if (UIUtil.isCloseClick(e) && myCaption.isWithinPanel(e)) {
            cancel();
          }
          else {
            super.mousePressed(e);
          }
        }
      };
      myCaption.addMouseListener(moveListener);
      myCaption.addMouseMotionListener(moveListener);
      final MyContentPanel saved = myContent;
      Disposer.register(this, new Disposable() {
        @Override
        public void dispose() {
          ListenerUtil.removeMouseListener(saved, moveListener);
          ListenerUtil.removeMouseMotionListener(saved, moveListener);
        }
      });
      myMoveListener = moveListener;
    }

    for (JBPopupListener listener : myListeners) {
      listener.beforeShown(new LightweightWindowEvent(this));
    }

    myPopup.setRequestFocus(myRequestFocus);
    myPopup.show();

    WindowAction.setEnabledFor(myPopup.getWindow(), myResizable);

    final Window window = getContentWindow(myContent);

    myWindow = window;

    myWindowListener = new MyWindowListener();
    window.addWindowListener(myWindowListener);

    if (myFocusable) {
      window.setFocusableWindowState(true);
      window.setFocusable(true);
    }

    if (myWindow != null) {
      // dialogwrapper-based popups do this internally through peer,
      // for other popups like jdialog-based we should exclude them manually, but
      // we still have to be able to use IdeFrame as parent
      if (!myMayBeParent && !(myWindow instanceof Frame)) {
        WindowManager.getInstance().doNotSuggestAsParent(myWindow);
      }
    }

    setMinimumSize(myMinSize);

    final Runnable afterShow = () -> {
      if (isDisposed()) {
        LOG.debug("popup is disposed after showing");
        removeActivity();
        return;
      }
      if (myPreferredFocusedComponent != null && myInStack && myFocusable) {
        myFocusTrackback.registerFocusComponent(myPreferredFocusedComponent);
        if (myPreferredFocusedComponent instanceof JTextComponent) {
          IJSwingUtilities.moveMousePointerOn(myPreferredFocusedComponent);
        }
      }

      removeActivity();

      afterShow();

    };

    if (myRequestFocus) {
      getPopupWindow().setFocusableWindowState(true);
      getPopupWindow().setFocusable(true);
      getFocusManager().requestFocus(new FocusCommand() {
        @NotNull
        @Override
        public ActionCallback run() {
          if (isDisposed()) {
            removeActivity();
            return ActionCallback.DONE;
          }

          _requestFocus();

          final ActionCallback result = new ActionCallback();

          final Runnable afterShowRunnable = () -> {
            afterShow.run();
            result.setDone();
          };
          if (myNativePopup) {
            final FocusRequestor furtherRequestor = getFocusManager().getFurtherRequestor();
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(() -> {
              if (isDisposed()) {
                result.setRejected();
                return;
              }

              furtherRequestor.requestFocus(new FocusCommand() {
                @NotNull
                @Override
                public ActionCallback run() {
                  if (isDisposed()) {
                    return ActionCallback.REJECTED;
                  }

                  _requestFocus();

                  afterShowRunnable.run();

                  return ActionCallback.DONE;
                }
              }, true).notify(result).doWhenProcessed(() -> removeActivity());
            });
          } else {
            afterShowRunnable.run();
          }

          return result;
        }
      }, true).doWhenRejected(afterShow);

      delayKeyEventsUntilFocusSettlesDown();
    } else {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (isDisposed()) {
          removeActivity();
          return;
        }

        afterShow.run();
      });
    }
    debugState("popup shown", State.SHOWING);
    myState = State.SHOWN;
  }

  private void delayKeyEventsUntilFocusSettlesDown() {
    ActionCallback typeAhead = new ActionCallback();
    getFocusManager().typeAheadUntil(typeAhead, "AbstractPopup");
    getFocusManager().doWhenFocusSettlesDown(() -> typeAhead.setDone());
  }

  public void focusPreferredComponent() {
    _requestFocus();
  }

  private void installProjectDisposer() {
    final Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (c != null) {
      final DataContext context = DataManager.getInstance().getDataContext(c);
      final Project project = CommonDataKeys.PROJECT.getData(context);
      if (project != null) {
        myProjectDisposable = new Disposable() {

          @Override
          public void dispose() {
            if (!AbstractPopup.this.isDisposed()) {
              Disposer.dispose(AbstractPopup.this);
            }
          }
        };
        Disposer.register(project, myProjectDisposable);
      }
    }
  }

  //Sometimes just after popup was shown the WINDOW_ACTIVATED cancels it
  private static void installWindowHook(final AbstractPopup popup) {
    if (popup.myCancelOnWindow) {
      popup.myCancelOnWindow = false;
      new Alarm(popup).addRequest(() -> popup.myCancelOnWindow = true, 100);
    }
  }

  private void addActivity() {
    UiActivityMonitor.getInstance().addActivity(myActivityKey);
  }

  private void removeActivity() {
    UiActivityMonitor.getInstance().removeActivity(myActivityKey);
  }

  private void prepareToShow() {
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        Point point = (Point)e.getPoint().clone();
        SwingUtilities.convertPointToScreen(point, e.getComponent());

        final Dimension dimension = myContent.getSize();
        dimension.height += myResizable && isToDrawMacCorner() ? AllIcons.General.MacCorner.getIconHeight() : 4;
        dimension.width += 4;
        Point locationOnScreen = myContent.getLocationOnScreen();
        final Rectangle bounds = new Rectangle(new Point(locationOnScreen.x - 2, locationOnScreen.y - 2), dimension);
        if (!bounds.contains(point)) {
          cancel();
        }
      }
    };
    myContent.addMouseListener(mouseAdapter);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myContent.removeMouseListener(mouseAdapter);
      }
    });

    myContent.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myCancelKeyEnabled) {
          cancel();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);


    mySearchKeyListener = new SpeedSearchKeyListener();
    myContent.addKeyListener(mySearchKeyListener);

    if (myCancelOnMouseOutCallback != null || myCancelOnWindow) {
      myMouseOutCanceller = new Canceller();
      Toolkit.getDefaultToolkit().addAWTEventListener(myMouseOutCanceller, AWTEvent.MOUSE_EVENT_MASK | WindowEvent.WINDOW_ACTIVATED |
                                                                           AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }


    ChildFocusWatcher focusWatcher = new ChildFocusWatcher(myContent) {
      @Override
      protected void onFocusGained(final FocusEvent event) {
        setWindowActive(true);
      }

      @Override
      protected void onFocusLost(final FocusEvent event) {
        setWindowActive(false);
      }
    };
    Disposer.register(this, focusWatcher);

    mySpeedSearchPatternField = new SearchTextField(false);
    mySpeedSearchPatternField.getTextEditor().setFocusable(false);
    if (SystemInfo.isMac) {
      RelativeFont.TINY.install(mySpeedSearchPatternField);
    }
  }

  private Window updateMaskAndAlpha(Window window) {
    if (window == null) return null;

    if (!window.isDisplayable() || !window.isShowing()) return window;

    final WindowManagerEx wndManager = getWndManager();
    if (wndManager == null) return window;

    if (!wndManager.isAlphaModeEnabled(window)) return window;

    if (myAlpha != myLastAlpha) {
      wndManager.setAlphaModeRatio(window, myAlpha);
      myLastAlpha = myAlpha;
    }

    if (myMaskProvider != null) {
      final Dimension size = window.getSize();
      Shape mask = myMaskProvider.getMask(size);
      wndManager.setWindowMask(window, mask);
    }

    WindowManagerEx.WindowShadowMode mode =
      myShadowed ? WindowManagerEx.WindowShadowMode.NORMAL : WindowManagerEx.WindowShadowMode.DISABLED;
    WindowManagerEx.getInstanceEx().setWindowShadow(window, mode);

    return window;
  }

  private static WindowManagerEx getWndManager() {
    return ApplicationManagerEx.getApplicationEx() != null ? WindowManagerEx.getInstanceEx() : null;
  }

  @Override
  public boolean isDisposed() {
    return myContent == null;
  }

  protected boolean beforeShow() {
    if (ApplicationManagerEx.getApplicationEx() == null) return true;
    StackingPopupDispatcher.getInstance().onPopupShown(this, myInStack);
    return true;
  }

  protected void afterShow() {
  }

  protected final boolean requestFocus() {
    if (!myFocusable) return false;

    getFocusManager().requestFocus(new FocusCommand() {
      @NotNull
      @Override
      public ActionCallback run() {
        _requestFocus();
        return ActionCallback.DONE;
      }
    }, true);

    return true;
  }

  private void _requestFocus() {
    if (!myFocusable) return;

    if (myPreferredFocusedComponent != null) {
      getGlobalInstance().doWhenFocusSettlesDown(() -> {
        if (!myDisposed) {
          getGlobalInstance().requestFocus(myPreferredFocusedComponent, true);
        }
      });
    }
  }

  private IdeFocusManager getFocusManager() {
    if (myProject != null) {
      return IdeFocusManager.getInstance(myProject);
    }
    if (myOwner != null) {
      return IdeFocusManager.findInstanceByComponent(myOwner);
    }
    return IdeFocusManager.findInstance();
  }

  private static JComponent getTargetComponent(Component aComponent) {
    if (aComponent instanceof JComponent) {
      return (JComponent)aComponent;
    }
    if (aComponent instanceof RootPaneContainer) {
      return ((RootPaneContainer)aComponent).getRootPane();
    }

    LOG.error("Cannot find target for:" + aComponent);
    return null;
  }

  private PopupComponent.Factory getFactory(boolean forceHeavyweight, boolean forceDialog) {
    if (Registry.is("allow.dialog.based.popups")) {
      boolean noFocus = !myFocusable || !myRequestFocus;
      boolean cannotBeDialog = noFocus; // && SystemInfo.isXWindow

      if (!cannotBeDialog && (isPersistent() || forceDialog)) {
        return new PopupComponent.Factory.Dialog();
      }
    }
    if (forceHeavyweight) {
      return new PopupComponent.Factory.AwtHeavyweight();
    }
    return new PopupComponent.Factory.AwtDefault();
  }

  @Override
  public JComponent getContent() {
    return myContent;
  }

  public void setLocation(RelativePoint p) {
    if (isBusy()) return;

    setLocation(p, myPopup);
  }

  private static void setLocation(final RelativePoint p, final PopupComponent popup) {
    if (popup == null) return;

    final Window wnd = popup.getWindow();
    assert wnd != null;

    wnd.setLocation(p.getScreenPoint());
  }

  @Override
  public void pack(boolean width, boolean height) {
    if (!isVisible() || !width && !height || isBusy()) return;

    Dimension size = getSize();
    Dimension prefSize = myContent.computePreferredSize();
    Point location = !myLocateWithinScreen ? null : getLocationOnScreen();
    Rectangle screen = location == null ? null : ScreenUtil.getScreenRectangle(location);

    if (width) {
      size.width = prefSize.width;
      if (screen != null) {
        int delta = screen.width + screen.x - location.x;
        if (size.width > delta) {
          size.width = delta;
          // we shrank horizontally - need to increase height to fit the horizontal scrollbar
          JScrollPane scrollPane = ScrollUtil.findScrollPane(myContent);
          if (scrollPane != null && scrollPane.getHorizontalScrollBarPolicy() != ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            JScrollBar scrollBar = scrollPane.getHorizontalScrollBar();
            if (scrollBar != null) {
              prefSize.height += scrollBar.getPreferredSize().height;
            }
          }
        }
      }
    }

    if (height) {
      size.height = prefSize.height;
      if (screen != null) {
        int delta = screen.height + screen.y - location.y;
        if (size.height > delta) {
          size.height = delta;
        }
      }
    }

    size.height += getAdComponentHeight();

    final Window window = getContentWindow(myContent);
    if (window != null) {
      window.setSize(size);
    }
  }

  @Deprecated
  public void pack() {
    if (isBusy()) return;

    final Window window = getContentWindow(myContent);
    if (window != null) {
      window.pack();
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setProject(Project project) {
    myProject = project;
  }


  @Override
  public void dispose() {
    if (myState == State.SHOWN) {
      LOG.debug("shown popup must be cancelled");
      cancel();
    }
    if (myState == State.DISPOSE) {
      return;
    }
    debugState("dispose popup", State.INIT, State.CANCEL);
    myState = State.DISPOSE;

    if (myDisposed) {
      return;
    }
    myDisposed = true;

    if (LOG.isDebugEnabled()) {
      LOG.debug("start disposing " + myContent);
    }

    Disposer.dispose(this, false);

    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myPopup != null) {
      cancel(myDisposeEvent);
    }

    if (myContent != null) {
      Container parent = myContent.getParent();
      if (parent != null) parent.remove(myContent);
      myContent.removeAll();
      myContent.removeKeyListener(mySearchKeyListener);
    }
    myContent = null;
    myPreferredFocusedComponent = null;
    myComponent = null;
    myFocusTrackback = null;
    myCallBack = null;
    myListeners = null;

    if (myMouseOutCanceller != null) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      // it may happen, but have no idea how
      // http://www.jetbrains.net/jira/browse/IDEADEV-21265
      if (toolkit != null) {
        toolkit.removeAWTEventListener(myMouseOutCanceller);
      }
    }
    myMouseOutCanceller = null;

    if (myFinalRunnable != null) {
      final ActionCallback typeAheadDone = new ActionCallback();
      IdeFocusManager.getInstance(myProject).typeAheadUntil(typeAheadDone);

      ModalityState modalityState = ModalityState.current();
      Runnable finalRunnable = myFinalRunnable;

      getFocusManager().doWhenFocusSettlesDown(() -> {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (ModalityState.current().equals(modalityState)) {
              ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(finalRunnable);
            }
            // Otherwise the UI has changed unexpectedly and the action is likely not applicable.
            // And we don't want finalRunnable to perform potentially destructive actions
            //   in the context of a suddenly appeared modal dialog.
          });
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(typeAheadDone.createSetDoneRunnable());
          myFinalRunnable = null;
      });
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("stop disposing content");
    }
  }

  private void resetWindow() {
    if (myWindow != null && getWndManager() != null) {
      getWndManager().resetWindow(myWindow);
      if (myWindowListener != null) {
        myWindow.removeWindowListener(myWindowListener);
      }

      if (myWindow instanceof RootPaneContainer) {
        RootPaneContainer container = (RootPaneContainer)myWindow;
        JRootPane root = container.getRootPane();
        root.putClientProperty(KEY, null);
        if (root.getGlassPane() instanceof IdeGlassPaneImpl) {
          // replace installed glass pane with the default one: JRootPane.createGlassPane()
          JPanel glass = new JPanel();
          glass.setName(root.getName() + ".glassPane");
          glass.setVisible(false);
          glass.setOpaque(false);
          root.setGlassPane(glass);
        }
      }

      myWindow = null;
      myWindowListener = null;
    }
  }

  public void storeDimensionSize(final Dimension size) {
    if (myDimensionServiceKey != null) {
      DimensionService.getInstance().setSize(myDimensionServiceKey, size, myProject);
    }
  }

  public void storeLocation(final Point xy) {
    if (myDimensionServiceKey != null) {
      DimensionService.getInstance().setLocation(myDimensionServiceKey, xy, myProject);
    }
  }

  public static class MyContentPanel extends JPanel implements DataProvider {
    private final boolean myResizable;
    private final boolean myDrawMacCorner;
    @Nullable private DataProvider myDataProvider;

    public MyContentPanel(final boolean resizable, final PopupBorder border, boolean drawMacCorner) {
      super(new BorderLayout());
      myResizable = resizable;
      myDrawMacCorner = drawMacCorner;
      setBorder(border);
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);

      if (myResizable && myDrawMacCorner) {
        AllIcons.General.MacCorner.paintIcon(this, g,
                                             getX() + getWidth() - AllIcons.General.MacCorner.getIconWidth(),
                                             getY() + getHeight() - AllIcons.General.MacCorner.getIconHeight());
      }
    }

    public Dimension computePreferredSize() {
      if (isPreferredSizeSet()) {
        Dimension setSize = getPreferredSize();
        setPreferredSize(null);
        Dimension result = getPreferredSize();
        setPreferredSize(setSize);
        return result;
      }
      return getPreferredSize();
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      return myDataProvider != null ? myDataProvider.getData(dataId) : null;
    }

    public void setDataProvider(@Nullable DataProvider dataProvider) {
      myDataProvider = dataProvider;
    }
  }

  public boolean isCancelOnClickOutside() {
    return myCancelOnClickOutside;
  }

  public boolean isCancelOnWindowDeactivation() {
    return myCancelOnWindowDeactivation;
  }

  private class Canceller implements AWTEventListener {
    private boolean myEverEntered = false;

    @Override
    public void eventDispatched(final AWTEvent event) {
      if (event.getID() == WindowEvent.WINDOW_ACTIVATED) {
        if (myCancelOnWindow && myPopup != null && !myPopup.isPopupWindow(((WindowEvent)event).getWindow())) {
          cancel();
        }
      }
      else if (event.getID() == MouseEvent.MOUSE_ENTERED) {
        if (withinPopup(event)) {
          myEverEntered = true;
        }
      }
      else if (event.getID() == MouseEvent.MOUSE_MOVED) {
        if (myCancelOnMouseOutCallback != null && myEverEntered && !withinPopup(event)) {
          if (myCancelOnMouseOutCallback.check((MouseEvent)event)) {
            cancel();
          }
        }
      }
    }

    private boolean withinPopup(final AWTEvent event) {
      if (!myContent.isShowing()) return false;

      final MouseEvent mouse = (MouseEvent)event;
      final Point point = mouse.getPoint();
      SwingUtilities.convertPointToScreen(point, mouse.getComponent());
      return new Rectangle(myContent.getLocationOnScreen(), myContent.getSize()).contains(point);
    }
  }

  @Override
  public void setLocation(@NotNull final Point screenPoint) {
    if (myPopup == null) {
      myForcedLocation = screenPoint;
    }
    else if (!isBusy()) {
      moveTo(myContent, screenPoint, myLocateByContent ? myHeaderPanel.getPreferredSize() : null);
    }
  }

  public static Window moveTo(JComponent content, Point screenPoint, final Dimension headerCorrectionSize) {
    final Window wnd = getContentWindow(content);
    if (wnd != null) {
      wnd.setCursor(Cursor.getDefaultCursor());
      if (headerCorrectionSize != null) {
        screenPoint.y -= headerCorrectionSize.height;
      }
      wnd.setLocation(screenPoint);
    }
    return wnd;
  }

  private static Window getContentWindow(Component content) {
    Window window = SwingUtilities.getWindowAncestor(content);
    if (window == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("no window ancestor for " + content);
      }
    }
    return window;
  }

  @Override
  public Point getLocationOnScreen() {
    Point screenPoint = myContent.getLocation();
    SwingUtilities.convertPointToScreen(screenPoint, myContent);
    return fixLocateByContent(screenPoint, false);
  }


  @Override
  public void setSize(@NotNull final Dimension size) {
    setSize(size, true);
  }

  private void setSize(Dimension size, boolean adjustByContent) {
    if (isBusy()) return;

    Dimension toSet = size;
    if (myPopup == null) {
      myForcedSize = toSet;
    }
    else {
      if (adjustByContent) {
        toSet.height += getAdComponentHeight();
      }
      updateMaskAndAlpha(setSize(myContent, toSet));
    }
  }

  private int getAdComponentHeight() {
    return myAdComponent != null && myAdComponent.isShowing() ? myAdComponent.getPreferredSize().height + JBUI.scale(1) : 0;
  }

  @Override
  public Dimension getSize() {
    if (myPopup != null) {
      final Window popupWindow = getContentWindow(myContent);
      if (popupWindow != null) {
        Dimension size = popupWindow.getSize();
        size.height -= getAdComponentHeight();
        return size;
      }
    }
    return myForcedSize;
  }

  @Override
  public void moveToFitScreen() {
    if (myPopup == null || isBusy()) return;

    final Window popupWindow = getContentWindow(myContent);
    if (popupWindow == null) return;
    Rectangle bounds = popupWindow.getBounds();

    ScreenUtil.moveRectangleToFitTheScreen(bounds);
    setLocation(bounds.getLocation());
    setSize(bounds.getSize(), false);
  }


  public static Window setSize(JComponent content, final Dimension size) {
    final Window popupWindow = getContentWindow(content);
    if (popupWindow == null) return null;
    JBInsets.addTo(size, content.getInsets());
    content.setPreferredSize(size);
    popupWindow.pack();
    return popupWindow;
  }

  public void setCaption(String title) {
    if (myCaption instanceof TitlePanel) {
      ((TitlePanel)myCaption).setText(title);
    }
  }

  private class MyWindowListener extends WindowAdapter {

    @Override
    public void windowOpened(WindowEvent e) {
      updateMaskAndAlpha(myWindow);
    }

    @Override
    public void windowClosing(final WindowEvent e) {
      resetWindow();
      cancel();
    }
  }

  @Override
  public boolean isPersistent() {
    return !myCancelOnClickOutside && !myCancelOnWindow;
  }

  @Override
  public boolean isNativePopup() {
    return myNativePopup;
  }

  @Override
  public void setUiVisible(final boolean visible) {
    if (myPopup != null) {
      if (visible) {
        myPopup.show();
        final Window window = getPopupWindow();
        if (window != null && myRestoreWindowSize != null) {
          window.setSize(myRestoreWindowSize);
          myRestoreWindowSize = null;
        }
      }
      else {
        final Window window = getPopupWindow();
        if (window != null) {
          myRestoreWindowSize = window.getSize();
          window.setVisible(true);
        }
      }
    }
  }

  public Window getPopupWindow() {
    return myPopup.getWindow();
  }

  public void setUserData(List<Object> userData) {
    myUserData = userData;
  }

  @Override
  public <T> T getUserData(final Class<T> userDataClass) {
    if (myUserData != null) {
      for (Object o : myUserData) {
        if (userDataClass.isInstance(o)) {
          @SuppressWarnings("unchecked") T t = (T)o;
          return t;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isModalContext() {
    return myModalContext;
  }

  @Override
  public boolean isFocused() {
    if (myComponent != null && isFocused(new Component[]{SwingUtilities.getWindowAncestor(myComponent)})) {
      return true;
    }
    return isFocused(myFocusOwners);
  }

  public static boolean isFocused(@Nullable Component[] components) {
    if (components == null) return false;

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    if (owner == null) return false;

    Window wnd = UIUtil.getWindow(owner);

    for (Component each : components) {
      if (each != null && SwingUtilities.isDescendingFrom(owner, each)) {
        Window eachWindow = UIUtil.getWindow(each);
        if (eachWindow == wnd) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public boolean isCancelKeyEnabled() {
    return myCancelKeyEnabled;
  }

  @NotNull
  public CaptionPanel getTitle() {
    return myCaption;
  }

  private void setHeaderComponent(JComponent c) {
    boolean doRevalidate = false;
    if (myHeaderComponent != null) {
      myHeaderPanel.remove(myHeaderComponent);
      myHeaderComponent = null;
      doRevalidate = true;
    }

    if (c != null) {
      myHeaderPanel.add(c, BorderLayout.CENTER);
      myHeaderComponent = c;

      final Dimension size = myContent.getSize();
      if (size.height < c.getPreferredSize().height * 2) {
        size.height += c.getPreferredSize().height;
        setSize(size);
      }

      doRevalidate = true;
    }

    if (doRevalidate) myContent.revalidate();
  }

  public void setWarning(@NotNull String text) {
    JBLabel label = new JBLabel(text, UIUtil.getBalloonWarningIcon(), SwingConstants.CENTER);
    label.setOpaque(true);
    Color color = HintUtil.getInformationColor();
    label.setBackground(color);
    label.setBorder(BorderFactory.createLineBorder(color, 3));
    myHeaderPanel.add(label, BorderLayout.SOUTH);
  }

  @Override
  public void addListener(final JBPopupListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(final JBPopupListener listener) {
    myListeners.remove(listener);
  }

  protected void onSpeedSearchPatternChanged() {
  }

  @Override
  public Component getOwner() {
    return myRequestorComponent;
  }

  @Override
  public void setMinimumSize(Dimension size) {
    //todo: consider changing only the caption panel minimum size
    Dimension sizeFromHeader = myHeaderPanel.getPreferredSize();

    if (sizeFromHeader == null) {
      sizeFromHeader = myHeaderPanel.getMinimumSize();
    }

    if (sizeFromHeader == null) {
      int minimumSize = myWindow.getGraphics().getFontMetrics(myHeaderPanel.getFont()).getHeight();
      sizeFromHeader = new Dimension(minimumSize, minimumSize);
    }

    if (size == null) {
      myMinSize = sizeFromHeader;
    } else {
      final int width = Math.max(size.width, sizeFromHeader.width);
      final int height = Math.max(size.height, sizeFromHeader.height);
      myMinSize = new Dimension(width, height);
    }

    if (myWindow != null) {
      Rectangle screenRectangle = ScreenUtil.getScreenRectangle(myWindow.getLocation());
      int width = Math.min(screenRectangle.width, myMinSize.width);
      int height = Math.min(screenRectangle.height, myMinSize.height);
      myWindow.setMinimumSize(new Dimension(width, height));
    }
  }

  @Override
  public void setFinalRunnable(Runnable finalRunnable) {
    myFinalRunnable = finalRunnable;
  }

  public void setOk(boolean ok) {
    myOk = ok;
  }

  @Override
  public void setDataProvider(@NotNull DataProvider dataProvider) {
    if (myContent != null) {
      myContent.setDataProvider(dataProvider);
    }
  }

  @Override
  public boolean dispatchKeyEvent(@NotNull KeyEvent e) {
    BooleanFunction<KeyEvent> handler = myKeyEventHandler;
    if (handler != null) {
      return handler.fun(e);
    }
    else {
      if (isCloseRequest(e) && myCancelKeyEnabled) {
        cancel(e);
        return true;
      }
    }
    return false;
  }

  private class SpeedSearchKeyListener implements KeyListener {
    @Override
    public void keyTyped(final KeyEvent e) {
      mySpeedSearch.process(e);
    }

    @Override
    public void keyPressed(final KeyEvent e) {
      mySpeedSearch.process(e);
    }

    @Override
    public void keyReleased(final KeyEvent e) {
      mySpeedSearch.process(e);
    }
  }

  @NotNull
  public Dimension getHeaderPreferredSize() {
    return myHeaderPanel.getPreferredSize();
  }
  @NotNull
  public Dimension getFooterPreferredSize() {
    return myAdComponent == null ? new Dimension(0,0) : myAdComponent.getPreferredSize();
  }

  public static boolean isCloseRequest(KeyEvent e) {
    return e != null && e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0;
  }

  private Point fixLocateByContent(Point location, boolean save) {
    Dimension size = !myLocateByContent ? null : myHeaderPanel.getPreferredSize();
    if (size != null) location.y -= save ? -size.height : size.height;
    return location;
  }

  protected boolean isBusy() {
    return myResizeListener != null && myResizeListener.isBusy() || myMoveListener != null && myMoveListener.isBusy();
  }

  /**
   * Returns the first frame (or dialog) ancestor of the component.
   * Note that this method returns the component itself if it is a frame (or dialog).
   *
   * @param component the component used to find corresponding frame (or dialog)
   * @return the first frame (or dialog) ancestor of the component; or {@code null}
   *         if the component is not a frame (or dialog) and is not contained inside a frame (or dialog)
   *
   * @see UIUtil#getWindow
   */
  private static Component getFrameOrDialog(Component component) {
    while (component != null) {
      if (component instanceof Frame || component instanceof Dialog) return component;
      component = component.getParent();
    }
    return null;
  }
}
