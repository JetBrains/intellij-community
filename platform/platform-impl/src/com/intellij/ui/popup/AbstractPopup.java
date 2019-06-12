// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.ui.ScreenAreaTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

import static java.awt.AWTEvent.MOUSE_EVENT_MASK;
import static java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK;
import static java.awt.event.MouseEvent.*;
import static java.awt.event.WindowEvent.WINDOW_ACTIVATED;
import static java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS;

public class AbstractPopup implements JBPopup, ScreenAreaTracker.ScreenAreaConsumer {
  public static final String SHOW_HINTS = "ShowHints";

  // Popup size stored with DimensionService is null first time
  // In this case you can put Dimension in content client properties to adjust size
  // Zero or negative values (with/height or both) would be ignored (actual values would be obtained from preferred size)
  public static final String FIRST_TIME_SIZE = "FirstTimeSize";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.AbstractPopup");

  private PopupComponent myPopup;
  private MyContentPanel myContent;
  private JComponent     myPreferredFocusedComponent;
  private boolean        myRequestFocus;
  private boolean        myFocusable;
  private boolean        myForcedHeavyweight;
  private boolean        myLocateWithinScreen;
  private boolean myResizable;
  private WindowResizeListener myResizeListener;
  private WindowMoveListener myMoveListener;
  private JPanel myHeaderPanel;
  private CaptionPanel myCaption;
  private JComponent myComponent;
  private String              myDimensionServiceKey;
  private Computable<Boolean> myCallBack;
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
  private   Dimension         myMinSize;
  private   List<Object>      myUserData;
  private   boolean           myShadowed;

  private float myAlpha;
  private float myLastAlpha;

  private MaskProvider myMaskProvider;

  private Window           myWindow;
  private boolean          myInStack;
  private MyWindowListener myWindowListener;

  private boolean myModalContext;

  private   Component[] myFocusOwners;
  private   PopupBorder myPopupBorder;
  private   Dimension   myRestoreWindowSize;
  protected Component   myOwner;
  private Component   myRequestorComponent;
  private   boolean     myHeaderAlwaysFocusable;
  private   boolean     myMovable;
  private   JComponent  myHeaderComponent;

  InputEvent myDisposeEvent;

  private Runnable myFinalRunnable;
  private Runnable myOkHandler;
  @Nullable private BooleanFunction<? super KeyEvent> myKeyEventHandler;

  protected boolean myOk;
  private final List<Runnable> myResizeListeners = new ArrayList<>();

  private static final WeakList<JBPopup> all = new WeakList<>();

  protected final SpeedSearch mySpeedSearch = new SpeedSearch() {
    boolean searchFieldShown;

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
  private JLabel myAdComponent;
  private boolean myDisposed;
  private boolean myNormalWindowLevel;

  private UiActivity myActivityKey;
  private Disposable myProjectDisposable;

  private volatile State myState = State.NEW;

  void setNormalWindowLevel(boolean normalWindowLevel) {
    myNormalWindowLevel = normalWindowLevel;
  }

  private enum State {NEW, INIT, SHOWING, SHOWN, CANCEL, DISPOSE}

  private void debugState(@NotNull String message, @NotNull State... states) {
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

  @NotNull
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
                     @NotNull List<? extends Pair<ActionListener, KeyStroke>> keyboardActions,
                     Component settingsButtons,
                     @Nullable final Processor<? super JBPopup> pinCallback,
                     boolean mayBeParent,
                     boolean showShadow,
                     boolean showBorder,
                     Color borderColor,
                     boolean cancelOnWindowDeactivation,
                     @Nullable BooleanFunction<? super KeyEvent> keyEventHandler) {
    assert !requestFocus || focusable : "Incorrect argument combination: requestFocus=true focusable=false";

    all.add(this);

    myActivityKey = new UiActivity.Focus("Popup:" + this);
    myProject = project;
    myComponent = component;
    myPopupBorder = showBorder ? borderColor != null ? PopupBorder.Factory.createColored(borderColor) :
                                 PopupBorder.Factory.create(true, showShadow) :
                                 PopupBorder.Factory.createEmpty();
    myShadowed = showShadow;
    myContent = createContentPanel(resizable, myPopupBorder, false);
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
        Icon icon = ToolWindowManagerEx.getInstanceEx(myProject != null ? myProject : ProjectUtil.guessCurrentProject((JComponent)myOwner))
          .getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab);
        myCaption.setButtonComponent(new InplaceButton(
          new IconButton("Open as Tool Window", icon),
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
    return new MyContentPanel(border);
  }

  public void setShowHints(boolean show) {
    final Window ancestor = getContentWindow(myComponent);
    if (ancestor instanceof RootPaneContainer) {
      final JRootPane rootPane = ((RootPaneContainer)ancestor).getRootPane();
      if (rootPane != null) {
        rootPane.putClientProperty(SHOW_HINTS, show);
      }
    }
  }

  public String getDimensionServiceKey() {
    return myDimensionServiceKey;
  }

  public void setDimensionServiceKey(@Nullable final String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
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
      myAdComponent = HintUtil.createAdComponent(s, JBUI.CurrentTheme.Advertiser.border(), alignment);
      JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.setOpaque(false);
      wrapper.add(myAdComponent, BorderLayout.CENTER);
      myContent.add(wrapper, BorderLayout.SOUTH);
      pack(false, true);
    }
    else {
      myAdComponent.setText(s);
      myAdComponent.setHorizontalAlignment(alignment);
    }
  }

  public static Point getCenterOf(final Component aContainer, final JComponent content) {
    return getCenterOf(aContainer, content.getPreferredSize());
  }

  private static Point getCenterOf(@NotNull Component aContainer, @NotNull Dimension contentSize) {
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
    if (UiInterceptors.tryIntercept(this)) return;
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
  public void showInCenterOf(@NotNull Component aComponent) {
    HelpTooltip.setMasterPopup(aComponent, this);
    Point popupPoint = getCenterOf(aComponent, getPreferredContentSize());
    show(aComponent, popupPoint.x, popupPoint.y, false);
  }


  @Override
  public void showUnderneathOf(@NotNull Component aComponent) {
    show(new RelativePoint(aComponent, new Point(JBUIScale.scale(2), aComponent.getHeight())));
  }

  @Override
  public void show(@NotNull RelativePoint aPoint) {
    if (UiInterceptors.tryIntercept(this)) return;
    HelpTooltip.setMasterPopup(aPoint.getOriginalComponent(), this);
    Point screenPoint = aPoint.getScreenPoint();
    show(aPoint.getComponent(), screenPoint.x, screenPoint.y, false);
  }

  @Override
  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {
    show(owner, point.x, point.y, false);
  }

  @NotNull
  @Override
  public Point getBestPositionFor(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null && editor.getComponent().isShowing()) {
      return getBestPositionFor(editor).getScreenPoint();
    }
    return relativePointByQuickSearch(dataContext).getScreenPoint();
  }

  @Override
  public void showInBestPositionFor(@NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
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

  @NotNull
  private RelativePoint relativePointByQuickSearch(@NotNull DataContext dataContext) {
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
    RelativePoint location;
    Component contextComponent = dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (contextComponent == myComponent) {
      location = new RelativePoint(myComponent, new Point());
    }
    else {
      location = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
    }
    if (myLocateWithinScreen) {
      Point screenPoint = location.getScreenPoint();
      Rectangle rectangle = new Rectangle(screenPoint, getSizeForPositioning());
      Rectangle screen = ScreenUtil.getScreenRectangle(screenPoint);
      ScreenUtil.moveToFit(rectangle, screen, null);
      location = new RelativePoint(rectangle.getLocation()).getPointOn(location.getComponent());
    }
    return location;
  }

  private Dimension getSizeForPositioning() {
    Dimension size = getSize();
    if (size == null && myDimensionServiceKey != null) {
      size = DimensionService.getInstance().getSize(myDimensionServiceKey, myProject);
    }
    if (size == null) {
      size = myContent.getPreferredSize();
    }
    return size;
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    // Intercept before the following assert; otherwise assertion may fail
    if (UiInterceptors.tryIntercept(this)) return;
    assert editor.getComponent().isShowing() : "Editor must be showing on the screen";

    // Set the accessible parent so that screen readers don't announce
    // a window context change -- the tooltip is "logically" hosted
    // inside the component (e.g. editor) it appears on top of.
    AccessibleContextUtil.setParent((Component)myComponent, editor.getContentComponent());
    show(getBestPositionFor(editor));
  }

  @NotNull
  private RelativePoint getBestPositionFor(@NotNull Editor editor) {
    DataContext context = ((EditorEx)editor).getDataContext();
    Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(context);
    if (dominantArea != null && !myRequestFocus) {
      final JLayeredPane layeredPane = editor.getContentComponent().getRootPane().getLayeredPane();
      return relativePointWithDominantRectangle(layeredPane, dominantArea);
    }
    else {
      return guessBestPopupLocation(editor);
    }
  }

  @NotNull
  private RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
    RelativePoint preferredLocation = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
    Dimension targetSize = getSizeForPositioning();
    Point preferredPoint = preferredLocation.getScreenPoint();
    Point result = getLocationAboveEditorLineIfPopupIsClippedAtTheBottom(preferredPoint, targetSize, editor);
    if (myLocateWithinScreen) {
      Rectangle rectangle = new Rectangle(result, targetSize);
      Rectangle screen = ScreenUtil.getScreenRectangle(preferredPoint);
      ScreenUtil.moveToFit(rectangle, screen, null);
      result = rectangle.getLocation();
    }
    return toRelativePoint(result, preferredLocation.getComponent());
  }

  @NotNull
  private static RelativePoint toRelativePoint(@NotNull Point screenPoint, @Nullable Component component) {
    if (component == null) {
      return RelativePoint.fromScreen(screenPoint);
    }
    SwingUtilities.convertPointFromScreen(screenPoint, component);
    return new RelativePoint(component, screenPoint);
  }

  @NotNull
  private static Point getLocationAboveEditorLineIfPopupIsClippedAtTheBottom(@NotNull Point originalLocation,
                                                                             @NotNull Dimension popupSize,
                                                                             @NotNull Editor editor) {
    Rectangle preferredBounds = new Rectangle(originalLocation, popupSize);
    Rectangle adjustedBounds = new Rectangle(preferredBounds);
    ScreenUtil.moveRectangleToFitTheScreen(adjustedBounds);
    if (preferredBounds.y - adjustedBounds.y <= 0) {
      return originalLocation;
    }
    int adjustedY = preferredBounds.y - editor.getLineHeight() - popupSize.height;
    if (adjustedY < 0) {
      return originalLocation;
    }
    return new Point(preferredBounds.x, adjustedY);
  }

  protected void addPopupListener(JBPopupListener listener) {
    myListeners.add(listener);
  }

  private RelativePoint relativePointWithDominantRectangle(final JLayeredPane layeredPane, final Rectangle bounds) {
    Dimension size = getSizeForPositioning();
    List<Supplier<Point>> optionsToTry = Arrays.asList(() -> new Point(bounds.x + bounds.width, bounds.y),
                                                       () -> new Point(bounds.x - size.width, bounds.y));
    for (Supplier<Point> option : optionsToTry) {
      Point location = option.get();
      SwingUtilities.convertPointToScreen(location, layeredPane);
      Point adjustedLocation = fitToScreenAdjustingVertically(location, size);
      if (adjustedLocation != null) return new RelativePoint(adjustedLocation).getPointOn(layeredPane);
    }

    setDimensionServiceKey(null); // going to cut width
    Point rightTopCorner = new Point(bounds.x + bounds.width, bounds.y);
    final Point rightTopCornerScreen = (Point)rightTopCorner.clone();
    SwingUtilities.convertPointToScreen(rightTopCornerScreen, layeredPane);
    Rectangle screen = ScreenUtil.getScreenRectangle(rightTopCornerScreen.x, rightTopCornerScreen.y);
    final int spaceOnTheLeft = bounds.x;
    final int spaceOnTheRight = screen.x + screen.width - rightTopCornerScreen.x;
    if (spaceOnTheLeft > spaceOnTheRight) {
      myComponent.setPreferredSize(new Dimension(spaceOnTheLeft, Math.max(size.height, JBUIScale.scale(200))));
      return new RelativePoint(layeredPane, new Point(0, bounds.y));
    }
    else {
      myComponent.setPreferredSize(new Dimension(spaceOnTheRight, Math.max(size.height, JBUIScale.scale(200))));
      return new RelativePoint(layeredPane, rightTopCorner);
    }
  }

  // positions are relative to screen
  @Nullable
  private static Point fitToScreenAdjustingVertically(@NotNull Point position, @NotNull Dimension size) {
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(position);
    Rectangle rectangle = new Rectangle(position, size);
    if (rectangle.height > screenRectangle.height ||
        rectangle.x < screenRectangle.x ||
        rectangle.x + rectangle.width > screenRectangle.x + screenRectangle.width) {
      return null;
    }
    ScreenUtil.moveToFit(rectangle, screenRectangle, null);
    return rectangle.getLocation();
  }

  @NotNull
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
    myFinalRunnable = FunctionUtil.composeRunnables(myOkHandler, myFinalRunnable);
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
      storeDimensionSize();
      if (myUseDimServiceForXYLocation) {
        final JRootPane root = myComponent.getRootPane();
        if (root != null) {
          Point location = getLocationOnScreen(root.getParent());
          if (location != null) {
            storeLocation(fixLocateByContent(location, true));
          }
        }
      }

      if (e instanceof MouseEvent) {
        IdeEventQueue.getInstance().blockNextEvents((MouseEvent)e);
      }

      myPopup.hide(false);

      if (ApplicationManager.getApplication() != null) {
        StackingPopupDispatcher.getInstance().onPopupHidden(this);
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

  private void disposePopup() {
    all.remove(this);
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
  public void show(@NotNull final Component owner) {
    show(owner, -1, -1, true);
  }

  public void show(@NotNull Component owner, int aScreenX, int aScreenY, final boolean considerForcedXY) {
    if (UiInterceptors.tryIntercept(this)) return;
    if (ApplicationManager.getApplication() != null && ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (isDisposed()) {
      throw new IllegalStateException("Popup was already disposed. Recreate a new instance to show again");
    }

    ApplicationManager.getApplication().assertIsDispatchThread();
    assert myState == State.INIT : "Popup was already shown. Recreate a new instance to show again.";

    debugState("show popup", State.INIT);
    myState = State.SHOWING;

    installProjectDisposer();
    addActivity();

    final boolean shouldShow = beforeShow();
    if (!shouldShow) {
      removeActivity();
      debugState("rejected to show popup", State.SHOWING);
      myState = State.INIT;
      return;
    }

    prepareToShow();
    installWindowHook(this);

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
      JBInsets.addTo(sizeToSet, myContent.getInsets());

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
    if (targetBounds.width != myContent.getWidth() || targetBounds.height != myContent.getHeight()) {
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
        myComponent,
        myMovable ? JBUI.insets(i) : JBUI.insets(0, 0, i, i),
        null) {
        private Cursor myCursor;

        @Override
        protected void setCursor(Component content, Cursor cursor) {
          if (myCursor != cursor || myCursor != Cursor.getDefaultCursor()) {
            glass.setCursor(cursor, this);
            myCursor = cursor;

            if (content instanceof JComponent) {
              IdeGlassPaneImpl.savePreProcessedCursor((JComponent)content, content.getCursor());
            }
            super.setCursor(content, cursor);
          }
        }

        @Override
        protected void notifyResized() {
          myResizeListeners.forEach(Runnable::run);
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
      Disposer.register(this, () -> {
        ListenerUtil.removeMouseListener(saved, moveListener);
        ListenerUtil.removeMouseMotionListener(saved, moveListener);
      });
      myMoveListener = moveListener;
    }

    for (JBPopupListener listener : myListeners) {
      listener.beforeShown(new LightweightWindowEvent(this));
    }

    myPopup.setRequestFocus(myRequestFocus);

    final Window window = getContentWindow(myContent);
    if (window instanceof IdeFrame) {
      LOG.warn("Lightweight popup is shown using AbstractPopup class. But this class is not supposed to work with lightweight popups.");
    }

    window.setFocusableWindowState(myRequestFocus);
    window.setFocusable(myRequestFocus);

    // Swing popup default always on top state is set in true
    window.setAlwaysOnTop(false);

    if (myFocusable) {
      FocusTraversalPolicy focusTraversalPolicy = new FocusTraversalPolicy() {
        @Override
        public Component getComponentAfter(Container aContainer, Component aComponent) {
          return getComponent();
        }

        private Component getComponent() {
          return myPreferredFocusedComponent == null ? myComponent : myPreferredFocusedComponent;
        }

        @Override
        public Component getComponentBefore(Container aContainer, Component aComponent) {
          return getComponent();
        }

        @Override
        public Component getFirstComponent(Container aContainer) {
          return getComponent();
        }

        @Override
        public Component getLastComponent(Container aContainer) {
          return getComponent();
        }

        @Override
        public Component getDefaultComponent(Container aContainer) {
          return getComponent();
        }
      };
      window.setFocusTraversalPolicy(focusTraversalPolicy);
      Disposer.register(this, () -> window.setFocusTraversalPolicy(null));
    }

    window.setAutoRequestFocus(myRequestFocus);

    final String data = getUserData(String.class);
    final boolean popupIsSimpleWindow = "TRUE".equals(getContent().getClientProperty("BookmarkPopup"));
    myContent.getRootPane().putClientProperty("SIMPLE_WINDOW", "SIMPLE_WINDOW".equals(data) || popupIsSimpleWindow);

    myWindow = window;
    if (myNormalWindowLevel) {
      myWindow.setType(Window.Type.NORMAL);
    }
    setMinimumSize(myMinSize);

    final Disposable tb = TouchBarsManager.showPopupBar(this, myContent);
    if (tb != null)
      Disposer.register(this, tb);

    myPopup.show();
    Rectangle bounds = window.getBounds();

    ScreenAreaTracker.register(this);

    if (bounds.width > screen.width || bounds.height > screen.height) {
      ScreenUtil.fitToScreen(bounds);
      window.setBounds(bounds);
    }

    WindowAction.setEnabledFor(myPopup.getWindow(), myResizable);

    myWindowListener = new MyWindowListener();
    window.addWindowListener(myWindowListener);

    if (myWindow != null) {
      // dialog wrapper-based popups do this internally through peer,
      // for other popups like jdialog-based we should exclude them manually, but
      // we still have to be able to use IdeFrame as parent
      if (!myMayBeParent && !(myWindow instanceof Frame)) {
        WindowManager.getInstance().doNotSuggestAsParent(myWindow);
      }
    }

    final Runnable afterShow = () -> {
      if (isDisposed()) {
        LOG.debug("popup is disposed after showing");
        removeActivity();
        return;
      }
      if ((myPreferredFocusedComponent instanceof AbstractButton || myPreferredFocusedComponent instanceof JTextField) && myFocusable) {
        IJSwingUtilities.moveMousePointerOn(myPreferredFocusedComponent);
      }

      removeActivity();

      afterShow();

    };

    if (myRequestFocus) {
      if (myPreferredFocusedComponent != null) {
        myPreferredFocusedComponent.requestFocus();
      }
      else {
        _requestFocus();
      }


      window.setAutoRequestFocus(myRequestFocus);

      SwingUtilities.invokeLater(afterShow);
    }
    else {
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

  public void focusPreferredComponent() {
    _requestFocus();
  }

  private void installProjectDisposer() {
    final Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (c != null) {
      final DataContext context = DataManager.getInstance().getDataContext(c);
      final Project project = CommonDataKeys.PROJECT.getData(context);
      if (project != null) {
        myProjectDisposable = () -> {
          if (!isDisposed()) {
            Disposer.dispose(this);
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
        Rectangle bounds = getBoundsOnScreen(myContent);
        if (bounds != null) {
          bounds.x -= 2;
          bounds.y -= 2;
          bounds.width += 4;
          bounds.height += 4;
        }
        if (bounds == null || !bounds.contains(e.getLocationOnScreen())) {
          cancel();
        }
      }
    };
    myContent.addMouseListener(mouseAdapter);
    Disposer.register(this, () -> myContent.removeMouseListener(mouseAdapter));

    myContent.addKeyListener(mySpeedSearch);

    if (myCancelOnMouseOutCallback != null || myCancelOnWindow) {
      myMouseOutCanceller = new Canceller();
      Toolkit.getDefaultToolkit().addAWTEventListener(myMouseOutCanceller,
                                                      MOUSE_EVENT_MASK | WINDOW_ACTIVATED | WINDOW_GAINED_FOCUS | MOUSE_MOTION_EVENT_MASK);
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

    mySpeedSearchPatternField = new SearchTextField(false) {
      @Override
      protected void onFieldCleared() {
        mySpeedSearch.reset();
      }
    };
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
    return ApplicationManager.getApplication() != null ? WindowManagerEx.getInstanceEx() : null;
  }

  @Override
  public boolean isDisposed() {
    return myContent == null;
  }

  protected boolean beforeShow() {
    if (ApplicationManager.getApplication() == null) return true;
    StackingPopupDispatcher.getInstance().onPopupShown(this, myInStack);
    return true;
  }

  protected void afterShow() {
  }

  protected final boolean requestFocus() {
    if (!myFocusable) return false;

    getFocusManager().doWhenFocusSettlesDown(() -> _requestFocus());

    return true;
  }

  private void _requestFocus() {
    if (!myFocusable) return;

    if (myPreferredFocusedComponent != null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        if (!myDisposed) {
          IdeFocusManager.getGlobalInstance().requestFocus(myPreferredFocusedComponent, true);
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

  @NotNull
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

  public JComponent getComponent() {
    return myComponent;
  }

  public Project getProject() {
    return myProject;
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
      myContent.removeKeyListener(mySpeedSearch);
    }
    myContent = null;
    myPreferredFocusedComponent = null;
    myComponent = null;
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
      IdeFocusManager.getInstance(myProject).typeAheadUntil(typeAheadDone, "Abstract Popup Disposal");

      ModalityState modalityState = ModalityState.current();
      Runnable finalRunnable = myFinalRunnable;

      getFocusManager().doWhenFocusSettlesDown(() -> {

        if (ModalityState.current().equals(modalityState)) {
          typeAheadDone.setDone();
          ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(finalRunnable);
        } else {
          typeAheadDone.setRejected();
          LOG.debug("Final runnable of popup is skipped");
        }
        // Otherwise the UI has changed unexpectedly and the action is likely not applicable.
        // And we don't want finalRunnable to perform potentially destructive actions
        //   in the context of a suddenly appeared modal dialog.
      });
      myFinalRunnable = null;
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

  public void storeDimensionSize() {
    if (myDimensionServiceKey != null) {
      Dimension size = myContent.getSize();
      JBInsets.removeFrom(size, myContent.getInsets());
      DimensionService.getInstance().setSize(myDimensionServiceKey, size, myProject);
    }
  }

  private void storeLocation(final Point xy) {
    if (myDimensionServiceKey != null) {
      DimensionService.getInstance().setLocation(myDimensionServiceKey, xy, myProject);
    }
  }

  public static class MyContentPanel extends JPanel implements DataProvider {
    @Nullable private DataProvider myDataProvider;

    @Deprecated
    public MyContentPanel(final boolean resizable, final PopupBorder border, boolean drawMacCorner) {
      this(border);
    }

    public MyContentPanel(PopupBorder border) {
      super(new BorderLayout());
      setBorder(border);
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
    public Object getData(@NotNull @NonNls String dataId) {
      return myDataProvider != null ? myDataProvider.getData(dataId) : null;
    }

    public void setDataProvider(@Nullable DataProvider dataProvider) {
      myDataProvider = dataProvider;
    }
  }

  boolean isCancelOnClickOutside() {
    return myCancelOnClickOutside;
  }

  boolean isCancelOnWindowDeactivation() {
    return myCancelOnWindowDeactivation;
  }

  private class Canceller implements AWTEventListener {
    private boolean myEverEntered;

    @Override
    public void eventDispatched(final AWTEvent event) {
      switch (event.getID()) {
        case WINDOW_ACTIVATED:
        case WINDOW_GAINED_FOCUS:
          if (myCancelOnWindow && myPopup != null && isCancelNeeded((WindowEvent)event, myPopup.getWindow())) {
            cancel();
          }
          break;
        case MOUSE_ENTERED:
          if (withinPopup(event)) {
            myEverEntered = true;
          }
          break;
        case MOUSE_MOVED:
        case MOUSE_PRESSED:
          if (myCancelOnMouseOutCallback != null && myEverEntered && !withinPopup(event)) {
            if (myCancelOnMouseOutCallback.check((MouseEvent)event)) {
              cancel();
            }
          }
          break;
      }
    }

    private boolean withinPopup(final AWTEvent event) {
      final MouseEvent mouse = (MouseEvent)event;
      Rectangle bounds = getBoundsOnScreen(myContent);
      return bounds != null && bounds.contains(mouse.getLocationOnScreen());
    }
  }

  @Override
  public void setLocation(@NotNull Point screenPoint) {
    if (myPopup == null) {
      myForcedLocation = screenPoint;
    }
    else if (!isBusy()) {
      final Insets insets = myContent.getInsets();
      if (insets != null && (insets.top != 0 || insets.left != 0)) {
        screenPoint = new Point(screenPoint.x - insets.left, screenPoint.y - insets.top);
      }
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

  @NotNull
  @Override
  public Point getLocationOnScreen() {
    Window window = getContentWindow(myContent);
    Point screenPoint = window == null ? new Point() : window.getLocation();
    fixLocateByContent(screenPoint, false);
    Insets insets = myContent.getInsets();
    if (insets != null) {
      screenPoint.x += insets.left;
      screenPoint.y += insets.top;
    }
    return screenPoint;
  }

  @Override
  public void setSize(@NotNull final Dimension size) {
    setSize(size, true);
  }

  private void setSize(@NotNull Dimension size, boolean adjustByContent) {
    if (isBusy()) return;

    Dimension toSet = new Dimension(size);
    if (adjustByContent) toSet.height += getAdComponentHeight();
    if (myPopup == null) {
      myForcedSize = toSet;
    }
    else {
      updateMaskAndAlpha(setSize(myContent, toSet));
    }
  }

  private int getAdComponentHeight() {
    return myAdComponent != null ? myAdComponent.getPreferredSize().height + JBUIScale.scale(1) : 0;
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

  @Override
  public void setCaption(@NotNull String title) {
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
          window.setVisible(false);
        }
      }
    }
  }

  public Window getPopupWindow() {
    return myPopup != null ? myPopup.getWindow() : null;
  }

  public void setUserData(List<Object> userData) {
    myUserData = userData;
  }

  @Override
  public <T> T getUserData(@NotNull final Class<T> userDataClass) {
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
  public void addListener(@NotNull final JBPopupListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull final JBPopupListener listener) {
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

  public void setOkHandler(Runnable okHandler) {
    myOkHandler = okHandler;
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
    BooleanFunction<? super KeyEvent> handler = myKeyEventHandler;
    if (handler != null && handler.fun(e)) {
      return true;
    }
    if (isCloseRequest(e) && myCancelKeyEnabled && !mySpeedSearch.isHoldingFilter()) {
      cancel(e);
      return true;
    }
    return false;
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
      if (component instanceof Window) return component;
      component = component.getParent();
    }
    return null;
  }

  @Nullable
  private static Point getLocationOnScreen(@Nullable Component component) {
    return component == null || !component.isShowing() ? null : component.getLocationOnScreen();
  }

  @Nullable
  private static Rectangle getBoundsOnScreen(@Nullable Component component) {
    Point point = getLocationOnScreen(component);
    return point == null ? null : new Rectangle(point, component.getSize());
  }

  @NotNull
  public static List<JBPopup> getChildPopups(@NotNull final Component component) {
    return ContainerUtil.filter(all.toStrongList(), popup -> {
      Component owner = popup.getOwner();
      while (owner != null) {
        if (owner.equals(component)) {
          return true;
        }
        owner = owner.getParent();
      }
      return false;
    });
  }

  @Override
  public boolean canShow() {
    return myState == State.INIT;
  }

  @NotNull
  @Override
  public Rectangle getConsumedScreenBounds() {
    return myWindow.getBounds();
  }

  @Override
  public Component getUnderlyingAreaOwner() {
    return myWindow.getOwner();
  }

  /**
   * Passed listener will be notified if popup is resized by user (using mouse)
   */
  public void addResizeListener(@NotNull Runnable runnable, @NotNull Disposable parentDisposable) {
    myResizeListeners.add(runnable);
    Disposer.register(parentDisposable, () -> myResizeListeners.remove(runnable));
  }

  /**
   * @return {@code true} if focus moved to a popup window or its child window
   */
  private static boolean isCancelNeeded(@NotNull WindowEvent event, Window window) {
    if (window == null) return true;
    Window focused = event.getWindow();
    return focused != window && (focused == null || window != focused.getOwner());
  }
}
