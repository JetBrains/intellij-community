// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diagnostic.LoadingState;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.ui.PopupLocationTracker;
import com.intellij.ide.ui.PopupLocator;
import com.intellij.ide.ui.ScreenAreaConsumer;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.AutoPopupSupportingListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static java.awt.event.MouseEvent.*;
import static java.awt.event.WindowEvent.WINDOW_ACTIVATED;
import static java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS;

public class AbstractPopup implements JBPopup, ScreenAreaConsumer, AlignedPopup {
  @NonNls public static final String SHOW_HINTS = "ShowHints";

  // Popup size stored with DimensionService is null first time
  // In this case you can put Dimension in content client properties to adjust size
  // Zero or negative values (with/height or both) would be ignored (actual values would be obtained from preferred size)
  @NonNls public static final String FIRST_TIME_SIZE = "FirstTimeSize";

  private static final Logger LOG = Logger.getInstance(AbstractPopup.class);

  private PopupComponent myPopup;
  private MyContentPanel myContent;
  private JComponent myPreferredFocusedComponent;
  private boolean myRequestFocus;
  private boolean myFocusable;
  private boolean myForcedHeavyweight;
  private boolean myLocateWithinScreen;
  private boolean myResizable;
  private WindowResizeListener myResizeListener;
  private WindowMoveListener myMoveListener;
  private JPanel myHeaderPanel;
  private CaptionPanel myCaption;
  private JComponent myComponent;
  private SpeedSearch mySpeedSearchFoundInRootComponent;
  private String myDimensionServiceKey;
  private Computable<Boolean> myCallBack;
  private Project myProject;
  private boolean myCancelOnClickOutside;
  private final List<JBPopupListener> myListeners = new CopyOnWriteArrayList<>();
  private boolean myUseDimServiceForXYLocation;
  private MouseChecker myCancelOnMouseOutCallback;
  private Canceller myMouseOutCanceller;
  private boolean myCancelOnWindow;
  private boolean myCancelOnWindowDeactivation = true;
  private Dimension myForcedSize;
  private Point myForcedLocation;
  private boolean myCancelKeyEnabled;
  private boolean myLocateByContent;
  private Dimension myMinSize;
  private List<Object> myUserData;
  private boolean myShadowed;

  private float myAlpha;
  private float myLastAlpha;

  private MaskProvider myMaskProvider;

  private Window myWindow;
  private boolean myInStack;
  private MyWindowListener myWindowListener;

  private boolean myModalContext;

  private Component[] myFocusOwners;
  private PopupBorder myPopupBorder;
  private Color myPopupBorderColor;
  private Dimension myRestoreWindowSize;
  protected Component myOwner;
  private Component myRequestorComponent;
  private boolean myHeaderAlwaysFocusable;
  private boolean myMovable;
  private JComponent myHeaderComponent;

  InputEvent myDisposeEvent;

  private Runnable myFinalRunnable;
  private Runnable myOkHandler;
  private @Nullable BooleanFunction<? super KeyEvent> myKeyEventHandler;

  protected boolean myOk;
  private final List<Runnable> myResizeListeners = new ArrayList<>();

  private static final WeakList<JBPopup> all = new WeakList<>();

  private boolean mySpeedSearchAlwaysShown;
  protected final SpeedSearch mySpeedSearch = new SpeedSearch() {
    boolean searchFieldShown;

    @Override
    public void update() {
      updateSpeedSearchColors(false);
      onSpeedSearchPatternChanged();
      mySpeedSearchPatternField.setText(getFilter());
      if (!mySpeedSearchAlwaysShown) {
        if (isHoldingFilter() && !searchFieldShown) {
          setHeaderComponent(mySpeedSearchPatternField);
          searchFieldShown = true;
        }
        else if (!isHoldingFilter() && searchFieldShown) {
          setHeaderComponent(null);
          searchFieldShown = false;
        }
      }
    }

    @Override
    public void noHits() {
      updateSpeedSearchColors(true);
    }
  };

  protected void updateSpeedSearchColors(boolean error) {
    JBTextField textEditor = mySpeedSearchPatternField.getTextEditor();
    if (ExperimentalUI.isNewUI()) {
      textEditor.setForeground(error ? NamedColorUtil.getErrorForeground() : UIUtil.getLabelForeground());
    }
    else {
      textEditor.setBackground(error ? LightColors.RED : UIUtil.getTextFieldBackground());
    }
  }

  protected SearchTextField mySpeedSearchPatternField;
  private boolean myNativePopup;
  private boolean myMayBeParent;
  private JComponent myAdComponent;
  private boolean myDisposed;
  private boolean myNormalWindowLevel;

  private UiActivity myActivityKey;
  private Disposable myProjectDisposable;

  private volatile State myState = State.NEW;
  private long myOpeningTime;

  void setNormalWindowLevel(boolean normalWindowLevel) {
    myNormalWindowLevel = normalWindowLevel;
  }

  private enum State {NEW, INIT, SHOWING, SHOWN, CANCEL, DISPOSE}

  private void debugState(@NonNls @NotNull String message, State @NotNull ... states) {
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

  protected @NotNull AbstractPopup init(Project project,
                                        @NotNull JComponent component,
                                        @Nullable JComponent preferredFocusedComponent,
                                        boolean requestFocus,
                                        boolean focusable,
                                        boolean movable,
                                        String dimensionServiceKey,
                                        boolean resizable,
                                        @NlsContexts.PopupTitle @Nullable String caption,
                                        @Nullable Computable<Boolean> callback,
                                        boolean cancelOnClickOutside,
                                        @NotNull Set<? extends JBPopupListener> listeners,
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
                                        Component @NotNull [] focusOwners,
                                        @Nullable @NlsContexts.PopupAdvertisement String adText,
                                        int adTextAlignment,
                                        boolean headerAlwaysFocusable,
                                        @NotNull List<? extends Pair<ActionListener, KeyStroke>> keyboardActions,
                                        Component settingsButtons,
                                        final @Nullable Processor<? super JBPopup> pinCallback,
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
    mySpeedSearchFoundInRootComponent =
      findInComponentHierarchy(component, it -> it instanceof ListWithFilter ? ((ListWithFilter<?>)it).getSpeedSearch() : null);
    myPopupBorder = showBorder ? borderColor != null ? PopupBorder.Factory.createColored(borderColor) :
                                 PopupBorder.Factory.create(true, showShadow) :
                                 PopupBorder.Factory.createEmpty();
    myPopupBorder.setPopupUsed();
    myShadowed = showShadow;
    if (showBorder && SystemInfo.isMac) {
      myPopupBorderColor = borderColor == null ? JBUI.CurrentTheme.Popup.borderColor(true) : borderColor;
    }
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

    myHeaderPanel = new JPanel(new BorderLayout()) {
      @Override
      public Color getBackground() {
        return JBUI.CurrentTheme.Popup.headerBackground(true);
      }
    };

    if (caption != null) {
      if (!caption.isEmpty()) {
        TitlePanel titlePanel = titleIcon == null ? new TitlePanel() : new TitlePanel(titleIcon.getRegular(), titleIcon.getInactive());
        titlePanel.setText(caption);
        titlePanel.setPopupTitle(ExperimentalUI.isNewUI());
        myCaption = titlePanel;
      }
      else {
        myCaption = new CaptionPanel();
      }

      if (pinCallback != null) {
        Icon icon = ToolWindowManager.getInstance(myProject != null ? myProject : ProjectUtil.guessCurrentProject((JComponent)myOwner))
          .getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab);
        myCaption.setButtonComponent(new InplaceButton(
          new IconButton(IdeBundle.message("show.in.find.window.button.name"), icon),
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
    myListeners.addAll(listeners);
    myUseDimServiceForXYLocation = useDimServiceForXYLocation;
    myCancelOnWindow = cancelOnWindow;
    myMinSize = minSize;

    if (Registry.is("ide.popup.horizontal.scroll.bar.opaque")) {
      forHorizontalScrollBar(bar -> bar.setOpaque(true));
    }

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


  protected @NotNull MyContentPanel createContentPanel(final boolean resizable, @NotNull PopupBorder border, boolean isToDrawMacCorner) {
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

  public void setDimensionServiceKey(final @Nullable String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
  }

  public void setAdText(@NotNull @NlsContexts.PopupAdvertisement String s) {
    setAdText(s, SwingConstants.LEFT);
  }

  public @NotNull PopupBorder getPopupBorder() {
    return myPopupBorder;
  }

  @Override
  public void setAdText(@NotNull @NlsContexts.PopupAdvertisement String s, int alignment) {
    JLabel label;
    if (myAdComponent == null || !(myAdComponent instanceof JLabel)) {
      label = HintUtil.createAdComponent(s, JBUI.CurrentTheme.Advertiser.border(), alignment);
      setFooterComponent(label);
    } else {
      label = (JLabel)myAdComponent;
    }

    Dimension prefSize = label.isVisible() ? myAdComponent.getPreferredSize() : JBUI.emptySize();
    boolean keepSize = BasicHTML.isHTMLString(s);

    label.setVisible(StringUtil.isNotEmpty(s));
    label.setText(keepSize ? s : wrapToSize(s));
    label.setHorizontalAlignment(alignment);

    Dimension newPrefSize = label.isVisible() ? myAdComponent.getPreferredSize() : JBUI.emptySize();
    int delta = newPrefSize.height - prefSize.height;

    // Resize popup to match new advertiser size.
    if (myPopup != null && !isBusy() && delta != 0 && !keepSize) {
      Window popupWindow = getContentWindow(myContent);
      if (popupWindow != null) {
        Dimension size = popupWindow.getSize();
        size.height += delta;
        myContent.setPreferredSize(size);
        popupWindow.pack();
        updateMaskAndAlpha(popupWindow);
      }
    }
  }

  protected void setFooterComponent(JComponent c) {
    if (myAdComponent != null) {
      myContent.remove(myAdComponent);
    }

    myContent.add(c, BorderLayout.SOUTH);
    pack(false, true);
    myAdComponent = c;
  }

  @NotNull
  @Nls
  private String wrapToSize(@NotNull @Nls String hint) {
    if (StringUtil.isEmpty(hint)) return hint;

    Dimension size = myContent.getSize();
    if (size.width == 0 && size.height == 0) {
      size = myContent.computePreferredSize();
    }

    JBInsets.removeFrom(size, myContent.getInsets());
    JBInsets.removeFrom(size, myAdComponent.getInsets());

    int width = Math.max(JBUI.CurrentTheme.Popup.minimumHintWidth(), size.width);
    return HtmlChunk.text(hint).wrapWith(HtmlChunk.div().attr("width", width)).wrapWith(HtmlChunk.html()).toString();
  }

  public static @NotNull Point getCenterOf(@NotNull Component aContainer, @NotNull JComponent content) {
    return getCenterOf(aContainer, content.getPreferredSize());
  }

  private static @NotNull Point getCenterOf(@NotNull Component aContainer, @NotNull Dimension contentSize) {
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

    WindowManagerEx manager = getWndManager();
    if (manager != null) {
      Component focusedComponent = manager.getFocusedComponent(project);
      if (focusedComponent != null) {
        Component parent = UIUtil.findUltimateParent(focusedComponent);
        if (parent instanceof Window) {
          window = (Window)parent;
        }
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }
    if ((window == null || !window.isShowing()) && manager != null) {
      window = manager.getFrame(project);
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
    showUnderneathOf(aComponent, true);
  }

  @Override
  public void showUnderneathOf(@NotNull Component aComponent, boolean useAlignment) {
    boolean isAlignmentUsed = ExperimentalUI.isNewUI() && Registry.is("ide.popup.align.by.content") && useAlignment
                              && isComponentSupportsAlignment(aComponent);
    var point = isAlignmentUsed ? pointUnderneathOfAlignedHorizontally(aComponent) : defaultPointUnderneathOf(aComponent);
    show(new RelativePoint(aComponent, point));
  }

  private boolean isComponentSupportsAlignment(Component c) {
    if (!(c instanceof JComponent)
        || (c instanceof ActionButton)
        || (c instanceof ComboBoxWithWidePopup<?>)) {
      return false;
    }

    return true;
  }

  @NotNull
  private static Point defaultPointUnderneathOf(@NotNull Component aComponent) {
    return new Point(JBUIScale.scale(2), aComponent.getHeight());
  }

  private static @NotNull Point pointUnderneathOfAlignedHorizontally(@NotNull Component comp) {
    if (!(comp instanceof JComponent jcomp)) return defaultPointUnderneathOf(comp);
    var result = new Point(calcHorizontalAlignment(jcomp), comp.getHeight());
    fitXToComponentScreen(result, comp);
    return result;
  }

  private static int calcHorizontalAlignment(JComponent jcomp) {
    int componentLeftInset = jcomp.getInsets().left;
    int popupLeftInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get() + JBUI.CurrentTheme.Popup.Selection.innerInsets().left;
    int res = componentLeftInset - popupLeftInset;
    if (jcomp instanceof AbstractButton button) {
      Insets margin = button.getMargin();
      if (margin != null) {
        res += margin.left;
      }
    }
    return res;
  }

  private static void fitXToComponentScreen(@NotNull Point point, @NotNull Component comp) {
    var componentScreen = ScreenUtil.getScreenRectangle(comp);
    SwingUtilities.convertPointToScreen(point, comp);
    if (point.x < componentScreen.x) {
      point.x = componentScreen.x;
    }
    if (point.x > componentScreen.x + componentScreen.width) {
      point.x = componentScreen.x + componentScreen.width;
    }
    SwingUtilities.convertPointFromScreen(point, comp);
  }

  @Override
  public void show(@NotNull RelativePoint aPoint) {
    if (UiInterceptors.tryIntercept(this, aPoint)) return;
    HelpTooltip.setMasterPopup(aPoint.getOriginalComponent(), this);
    Point screenPoint = aPoint.getScreenPoint();
    show(aPoint.getComponent(), screenPoint.x, screenPoint.y, false);
  }

  @Override
  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {
    show(owner, point.x, point.y, false);
  }

  @Override
  public @NotNull Point getBestPositionFor(@NotNull DataContext dataContext) {
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

  private @NotNull RelativePoint relativePointByQuickSearch(@NotNull DataContext dataContext) {
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
    Component contextComponent = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
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

  public Dimension getSizeForPositioning() {
    Dimension size = getSize();
    if (size == null) {
      size = getStoredSize();
    }
    if (size == null) {
      Dimension contentPreferredSize = myContent.getPreferredSize();
      Dimension titlePreferredSize = getTitle().getPreferredSize();
      size = new JBDimension(Math.max(contentPreferredSize.width, titlePreferredSize.width),
                             contentPreferredSize.height + titlePreferredSize.height, true);
    }
    return size;
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    // Intercept before the following assert; otherwise assertion may fail
    if (UiInterceptors.tryIntercept(this)) return;
    assert UIUtil.isShowing(editor.getContentComponent()) : "Editor must be showing on the screen";

    // Set the accessible parent so that screen readers don't announce
    // a window context change -- the tooltip is "logically" hosted
    // inside the component (e.g. editor) it appears on top of.
    AccessibleContextUtil.setParent(myComponent, editor.getContentComponent());
    show(getBestPositionFor(editor));
  }

  @ApiStatus.Internal
  public final @NotNull RelativePoint getBestPositionFor(@NotNull Editor editor) {
    if (editor instanceof EditorEx) {
      DataContext context = ((EditorEx)editor).getDataContext();
      PopupLocator popupLocator = PlatformDataKeys.CONTEXT_MENU_LOCATOR.getData(context);
      if (popupLocator != null) {
        Point result = popupLocator.getPositionFor(this);
        if (result != null) return new RelativePoint(result);
      }
      Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(context);
      if (dominantArea != null && !myRequestFocus) {
        final JLayeredPane layeredPane = editor.getContentComponent().getRootPane().getLayeredPane();
        return relativePointWithDominantRectangle(layeredPane, dominantArea);
      }
    }

    return guessBestPopupLocation(editor);
  }

  private @NotNull RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
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

  private static @NotNull RelativePoint toRelativePoint(@NotNull Point screenPoint, @Nullable Component component) {
    if (component == null) {
      return RelativePoint.fromScreen(screenPoint);
    }
    SwingUtilities.convertPointFromScreen(screenPoint, component);
    return new RelativePoint(component, screenPoint);
  }

  private static @NotNull Point getLocationAboveEditorLineIfPopupIsClippedAtTheBottom(@NotNull Point originalLocation,
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

  private @NotNull RelativePoint relativePointWithDominantRectangle(@NotNull JLayeredPane layeredPane, @NotNull Rectangle bounds) {
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
  private static @Nullable Point fitToScreenAdjustingVertically(@NotNull Point position, @NotNull Dimension size) {
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

  public @NotNull Dimension getPreferredContentSize() {
    if (myForcedSize != null) {
      return myForcedSize;
    }
    Dimension size = getStoredSize();
    if (size != null) return size;
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
    InputEvent inputEvent = null;
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (event instanceof InputEvent ie && myPopup != null) {
      Window window = myPopup.getWindow();
      if (window != null && UIUtil.isDescendingFrom(ie.getComponent(), window)) {
        inputEvent = ie;
      }
    }
    cancel(inputEvent);
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

    if (LOG.isTraceEnabled()) LOG.trace(new Exception("cancel popup stack trace"));

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
    }

    myListeners.forEach(listener -> listener.onClosed(new LightweightWindowEvent(this, myOk)));

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
    return (myCallBack == null || myCallBack.compute().booleanValue()) && !preventImmediateClosingAfterOpening();
  }

  private boolean preventImmediateClosingAfterOpening() {
    // this is a workaround for X.Org bug https://gitlab.freedesktop.org/xorg/xserver/-/issues/1347
    // it affects only non-override-redirect windows, hence the check for myNormalWindowLevel
    return UIUtil.isXServerOnWindows() &&
           myNormalWindowLevel &&
           IdeEventQueue.getInstance().getTrueCurrentEvent().getID() == WindowEvent.WINDOW_DEACTIVATED &&
           System.currentTimeMillis() < myOpeningTime + 1000;
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
  public void show(final @NotNull Component owner) {
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

    Dimension sizeToSet = getStoredSize();
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
    if (myUseDimServiceForXYLocation) {
      Point storedLocation = getStoredLocation();
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
      StringBuilder sb = new StringBuilder("popup preferred size is bigger than screen: ");
      sb.append(targetBounds.width).append("x").append(targetBounds.height);
      IJSwingUtilities.appendComponentClassNames(sb, myContent);
      LOG.warn(sb.toString());
    }
    Rectangle original = new Rectangle(targetBounds);
    if (myLocateWithinScreen) {
      ScreenUtil.moveToFit(targetBounds, screen, null);
    }
    else {
      //even when LocateWithinScreen option is disabled, popup should not be shown in invisible area
      fitToVisibleArea(targetBounds);
    }


    if (myMouseOutCanceller != null) {
      myMouseOutCanceller.myEverEntered = targetBounds.equals(original);
    }

    // prevent hiding of a floating toolbar
    Point pointOnOwner = new Point(aScreenX, aScreenY);
    SwingUtilities.convertPointFromScreen(pointOnOwner, owner);
    if (ActionToolbarImpl.isInPopupToolbar(SwingUtilities.getDeepestComponentAt(owner, pointOnOwner.x, pointOnOwner.y))) {
      AutoPopupSupportingListener.installOn(this);
    }

    myOwner = getFrameOrDialog(owner); // use correct popup owner for non-modal dialogs too
    if (myOwner == null) {
      myOwner = owner;
    }

    myRequestorComponent = owner;

    boolean forcedDialog = myMayBeParent || SystemInfo.isMac && !(myOwner instanceof IdeFrame) && myOwner.isShowing();

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

      WindowResizeListener resizeListener = new WindowResizeListener(
        myComponent,
        myMovable ? JBUI.insets(4) : JBUI.insets(0, 0, 4, 4),
        null) {
        private Cursor myCursor;

        @Override
        protected void setCursor(@NotNull Component content, Cursor cursor) {
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

    setIsMovable(myMovable);

    notifyListeners();

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

    if (WindowRoundedCornersManager.isAvailable()) {
      PopupCornerType cornerType = getUserData(PopupCornerType.class);
      if (cornerType == null) {
        cornerType = PopupCornerType.RoundedWindow;
      }
      if (cornerType != PopupCornerType.None) {
        if ((SystemInfoRt.isMac && myPopupBorderColor != null && UIUtil.isUnderDarcula()) || SystemInfoRt.isWindows) {
          WindowRoundedCornersManager.setRoundedCorners(window, new Object[]{cornerType,
            SystemInfoRt.isWindows ? JBUI.CurrentTheme.Popup.borderColor(true) : myPopupBorderColor});
          myContent.setBorder(myPopupBorder = PopupBorder.Factory.createEmpty());
        }
        else {
          WindowRoundedCornersManager.setRoundedCorners(window, cornerType);
        }
      }
    }

    myWindow = window;
    if (myNormalWindowLevel) {
      myWindow.setType(Window.Type.NORMAL);
    }
    setMinimumSize(myMinSize);

    TouchbarSupport.showPopupItems(this, myContent);

    myPopup.show();
    Rectangle bounds = window.getBounds();

    PopupLocationTracker.register(this);

    if (bounds.width > screen.width || bounds.height > screen.height) {
      ScreenUtil.fitToScreen(bounds);
      window.setBounds(bounds);
    }

    if (LOG.isDebugEnabled()) {
      GraphicsDevice device = ScreenUtil.getScreenDevice(bounds);
      StringBuilder sb = new StringBuilder("Popup is shown with bounds " + bounds);
      if (device != null) sb.append(" on screen with ID \"").append(device.getIDstring()).append("\"");
      LOG.debug(sb.toString());
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
    myOpeningTime = System.currentTimeMillis();

    afterShowSync();
  }

  public void notifyListeners() {
    myListeners.forEach(listener -> listener.beforeShown(new LightweightWindowEvent(this)));
  }

  private static void fitToVisibleArea(Rectangle targetBounds) {
    Point topLeft = new Point(targetBounds.x, targetBounds.y);
    Point bottomRight = new Point((int)targetBounds.getMaxX(), (int)targetBounds.getMaxY());
    Rectangle topLeftScreen = ScreenUtil.getScreenRectangle(topLeft);
    Rectangle bottomRightScreen = ScreenUtil.getScreenRectangle(bottomRight);
    if (topLeft.x < topLeftScreen.x || topLeft.y < topLeftScreen.y
        || bottomRight.x > bottomRightScreen.getMaxX() || bottomRight.y > bottomRightScreen.getMaxY()) {
      GraphicsDevice device = ScreenUtil.getScreenDevice(targetBounds);
      Rectangle mostAppropriateScreenRectangle = device != null ? ScreenUtil.getScreenRectangle(device.getDefaultConfiguration())
                                                                : ScreenUtil.getMainScreenBounds();
      ScreenUtil.moveToFit(targetBounds, mostAppropriateScreenRectangle, null);
    }
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
  private static void installWindowHook(final @NotNull AbstractPopup popup) {
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
                                                      WINDOW_EVENT_MASK | MOUSE_EVENT_MASK | MOUSE_MOTION_EVENT_MASK);
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
    mySpeedSearchPatternField.getTextEditor().setFocusable(mySpeedSearchAlwaysShown);

    JBTextField textField = mySpeedSearchPatternField.getTextEditor();
    if (ExperimentalUI.isNewUI()) {
      mySpeedSearchPatternField.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
      textField.setOpaque(false);
      textField.putClientProperty("TextFieldWithoutMargins", true);
      textField.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true);
      textField.putClientProperty("TextField.NoMinHeightBounds", true);
      EmptyBorder outsideBorder = new EmptyBorder(JBUI.CurrentTheme.Popup.searchFieldBorderInsets());
      Border lineBorder = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorColor(), 0, 0, 1, 0);
      mySpeedSearchPatternField.setBorder(JBUI.Borders.compound(outsideBorder, lineBorder,
                                                                new EmptyBorder(JBUI.CurrentTheme.Popup.searchFieldInputInsets())));
      textField.setBorder(JBUI.Borders.empty());
    } else {
      if (mySpeedSearchAlwaysShown) {
        mySpeedSearchPatternField.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 1, 0));
        textField.setBorder(JBUI.Borders.empty());
      }
    }
    if (SystemInfo.isMac) {
      RelativeFont.TINY.install(mySpeedSearchPatternField);
    }

    if (mySpeedSearchAlwaysShown) {
      setHeaderComponent(mySpeedSearchPatternField);
    }
  }

  private void updateMaskAndAlpha(Window window) {
    if (window == null) return;

    if (!window.isDisplayable() || !window.isShowing()) return;

    final WindowManagerEx wndManager = getWndManager();
    if (wndManager == null) return;

    if (!wndManager.isAlphaModeEnabled(window)) return;

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
  }

  private static WindowManagerEx getWndManager() {
    return ApplicationManager.getApplication() != null ? WindowManagerEx.getInstanceEx() : null;
  }

  @Override
  public boolean isDisposed() {
    return myContent == null;
  }

  protected boolean beforeShow() {
    if (ApplicationManager.getApplication() == null || !LoadingState.COMPONENTS_REGISTERED.isOccurred()) return true;
    StackingPopupDispatcher.getInstance().onPopupShown(this, myInStack);
    return true;
  }

  protected void afterShow() {
  }

  protected void afterShowSync() {
  }

  protected final boolean requestFocus() {
    if (!myFocusable) return false;

    getFocusManager().doWhenFocusSettlesDown(() -> _requestFocus());

    return true;
  }

  private void _requestFocus() {
    if (!myFocusable) return;

    JComponent toFocus = ObjectUtils.chooseNotNull(myPreferredFocusedComponent,
                                                   mySpeedSearchAlwaysShown ? mySpeedSearchPatternField : null);
    if (toFocus != null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        if (!myDisposed) {
          IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true);
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

  private PopupComponent.@NotNull Factory getFactory(boolean forceHeavyweight, boolean forceDialog) {
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
  public @NotNull JComponent getContent() {
    return myContent;
  }

  public void setLocation(@NotNull RelativePoint p) {
    if (isBusy()) return;

    setLocation(p, myPopup);
  }

  private static void setLocation(@NotNull RelativePoint p, @Nullable PopupComponent popup) {
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
        }
      }
    }

    if (height) {
      if (size.width < prefSize.width) {
        if (!SystemInfo.isMac || Registry.is("mac.scroll.horizontal.gap")) {
          // we shrank horizontally - need to increase height to fit the horizontal scrollbar
          forHorizontalScrollBar(bar -> prefSize.height += bar.getPreferredSize().height);
        }
      }
      size.height = prefSize.height;
      if (screen != null) {
        int delta = screen.height + screen.y - location.y;
        if (size.height > delta) {
          size.height = delta;
        }
      }
    }
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
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    mySpeedSearchFoundInRootComponent = null;
    myCallBack = null;
    myListeners.clear();

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
      Runnable finalRunnable = myFinalRunnable;
      getFocusManager().doWhenFocusSettlesDown(() -> {
        try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
          finalRunnable.run();
        }
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

      if (myWindow instanceof RootPaneContainer container) {
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

  private @Nullable Project getProjectDependingOnKey(String key) {
    return !key.startsWith(WindowStateService.USE_APPLICATION_WIDE_STORE_KEY_PREFIX) ? myProject : null;
  }

  public final @NotNull Dimension getContentSize() {
    Dimension size = myContent.getSize();
    JBInsets.removeFrom(size, myContent.getInsets());
    return size;
  }

  public void storeDimensionSize() {
    if (myDimensionServiceKey != null) {
      getWindowStateService(getProjectDependingOnKey(myDimensionServiceKey)).putSize(myDimensionServiceKey, getContentSize());
    }
  }

  private void storeLocation(final Point xy) {
    if (myDimensionServiceKey != null) {
      getWindowStateService(getProjectDependingOnKey(myDimensionServiceKey)).putLocation(myDimensionServiceKey, xy);
    }
  }

  public static class MyContentPanel extends JPanel implements DataProvider {
    private @Nullable DataProvider myDataProvider;

    public MyContentPanel(@NotNull PopupBorder border) {
      super(new BorderLayout());
      MnemonicHelper.init(this);
      putClientProperty(UIUtil.TEXT_COPY_ROOT, Boolean.TRUE);
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

    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
      return myDataProvider != null ? myDataProvider.getData(dataId) : null;
    }

    public void setDataProvider(@Nullable DataProvider dataProvider) {
      myDataProvider = dataProvider;
    }
  }

  @ApiStatus.Internal
  public boolean isCancelOnClickOutside() {
    return myCancelOnClickOutside;
  }

  @ApiStatus.Internal
  public void setCancelOnClickOutside(boolean cancelOnClickOutside) {
    myCancelOnClickOutside = cancelOnClickOutside;
  }

  @ApiStatus.Internal
  public void setIsMovable(boolean movable) {
    myMovable = movable;

    if (!myMovable && myMoveListener != null) {
      final MyContentPanel saved = myContent;
      ListenerUtil.removeMouseListener(saved, myMoveListener);
      ListenerUtil.removeMouseMotionListener(saved, myMoveListener);
      myMoveListener = null;
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
  }

  @ApiStatus.Internal
  public void setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation) {
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;
  }

  boolean isCancelOnWindowDeactivation() {
    return myCancelOnWindowDeactivation;
  }

  private class Canceller implements AWTEventListener {
    private boolean myEverEntered;

    @Override
    public void eventDispatched(final AWTEvent event) {
      switch (event.getID()) {
        case WINDOW_ACTIVATED, WINDOW_GAINED_FOCUS -> {
          if (myCancelOnWindow && myPopup != null && isCancelNeeded((WindowEvent)event, myPopup.getWindow())) {
            cancel();
          }
        }
        case MOUSE_ENTERED -> {
          if (withinPopup(event)) {
            myEverEntered = true;
          }
        }
        case MOUSE_MOVED, MOUSE_PRESSED -> {
          if (myCancelOnMouseOutCallback != null && myEverEntered && !withinPopup(event)) {
            if (myCancelOnMouseOutCallback.check((MouseEvent)event)) {
              cancel();
            }
          }
        }
      }
    }

    private boolean withinPopup(@NotNull AWTEvent event) {
      final MouseEvent mouse = (MouseEvent)event;
      Rectangle bounds = getBoundsOnScreen(myContent);
      return bounds != null && bounds.contains(mouse.getLocationOnScreen());
    }
  }

  @Override
  public void setLocation(@NotNull Point screenPoint) {
    // do not update the bounds programmatically if the user moves or resizes the popup
    if (!isBusy()) setBounds(new Point(screenPoint), null);
  }

  private static Window getContentWindow(@NotNull Component content) {
    Window window = SwingUtilities.getWindowAncestor(content);
    if (window == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("no window ancestor for " + content);
      }
    }
    return window;
  }

  @Override
  public @NotNull Point getLocationOnScreen() {
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
  public void setSize(final @NotNull Dimension size) {
    // do not update the bounds programmatically if the user moves or resizes the popup
    if (!isBusy()) {
      setBounds(null, new Dimension(size));
      if (myPopup != null) Optional.ofNullable(getContentWindow(myContent)).ifPresent(Container::validate); // to adjust content size
    }
  }

  public int getAdComponentHeight() {
    return myAdComponent != null ? myAdComponent.getPreferredSize().height + JBUIScale.scale(1) : 0;
  }

  protected boolean isAdVisible() {
    return myAdComponent != null && myAdComponent.isVisible();
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
    // calling #setLocation or #setSize makes the window move for a bit because of tricky computations
    // our aim here is to just move the window as-is to make it fit the screen
    // no tricks are included here
    popupWindow.setBounds(bounds);
    updateMaskAndAlpha(popupWindow);
  }

  @Deprecated(forRemoval = true)
  public static Window setSize(@NotNull JComponent content, @NotNull Dimension size) {
    final Window popupWindow = getContentWindow(content);
    if (popupWindow == null) return null;
    JBInsets.addTo(size, content.getInsets());
    content.setPreferredSize(size);
    popupWindow.pack();
    return popupWindow;
  }

  @Override
  public void setBounds(@NotNull Rectangle bounds) {
    // do not update the bounds programmatically if the user moves or resizes the popup
    if (!isBusy()) setBounds(bounds.getLocation(), bounds.getSize());
  }

  /**
   * Updates the popup location and size at once.
   * Note that this internal implementation modifies input parameters.
   *
   * @param location a new popup location if needed
   * @param size     a new popup size if needed
   */
  private void setBounds(@Nullable Point location, @Nullable Dimension size) {
    if (size != null) size.height += getAdComponentHeight();
    if (myPopup == null) {
      // store bounds to show popup later
      if (location != null) myForcedLocation = location;
      if (size != null) myForcedSize = size;
    }
    else {
      MyContentPanel content = myContent;
      if (content == null) return;
      Window window = getContentWindow(content);
      if (window == null) return;
      Insets insets = content.getInsets();
      if (location == null) {
        location = window.getLocation(); // use current window location
      }
      else {
        fixLocateByContent(location, false);
        if (insets != null) {
          location.x -= insets.left;
          location.y -= insets.top;
        }
      }
      if (size == null) {
        size = window.getSize(); // use current window size
      }
      else {
        JBInsets.addTo(size, insets);
        content.setPreferredSize(size);
        size = window.getPreferredSize();
      }
      window.setBounds(location.x, location.y, size.width, size.height);
      //window.validate();
      window.setCursor(Cursor.getDefaultCursor());
      updateMaskAndAlpha(window);
    }
  }

  @Override
  public void setCaption(@NotNull @NlsContexts.PopupTitle String title) {
    if (myCaption instanceof TitlePanel) {
      ((TitlePanel)myCaption).setText(title);
    }
  }

  protected void setSpeedSearchAlwaysShown() {
    assert myState == State.INIT;
    mySpeedSearchAlwaysShown = true;
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

  @Override
  public void setUserData(@NotNull List<Object> userData) {
    myUserData = userData;
  }

  @Override
  public <T> T getUserData(final @NotNull Class<T> userDataClass) {
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

  private static boolean isFocused(Component @NotNull [] components) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    if (owner == null) return false;

    Window wnd = ComponentUtil.getWindow(owner);

    for (Component each : components) {
      if (each != null && SwingUtilities.isDescendingFrom(owner, each)) {
        Window eachWindow = ComponentUtil.getWindow(each);
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

  public @NotNull CaptionPanel getTitle() {
    return myCaption;
  }

  protected void setHeaderComponent(JComponent c) {
    boolean doRevalidate = false;
    if (myHeaderComponent != null) {
      myHeaderPanel.remove(myHeaderComponent);
      myHeaderComponent = null;
      doRevalidate = true;
    }

    if (c != null) {
      myHeaderPanel.add(c, BorderLayout.CENTER);
      myHeaderComponent = c;

      if (isVisible()) {
        final Dimension size = myContent.getSize();
        if (size.height < c.getPreferredSize().height * 2) {
          size.height += c.getPreferredSize().height;
          setSize(size);
        }
      }

      doRevalidate = true;
    }

    if (doRevalidate) myContent.revalidate();
  }

  public void setWarning(@NotNull @NlsContexts.Label String text) {
    myHeaderPanel.add(createWarning(text), BorderLayout.SOUTH);
  }

  protected @NotNull JComponent createWarning(@NotNull @NlsContexts.Label String text) {
    JBLabel label = new JBLabel(text, UIUtil.getBalloonWarningIcon(), SwingConstants.CENTER);
    label.setOpaque(true);
    Color color = HintUtil.getInformationColor();
    label.setBackground(color);
    label.setBorder(BorderFactory.createLineBorder(color, 3));
    return label;
  }

  @Override
  public void addListener(@NotNull JBPopupListener listener) {
    myListeners.add(0, listener); // last added first notified
  }

  @Override
  public void removeListener(@NotNull JBPopupListener listener) {
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
    Dimension sizeFromHeader = calcHeaderSize();

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

  public Dimension getMinimumSize() {
    return myMinSize != null ? myMinSize : calcHeaderSize();
  }

  @NotNull
  private Dimension calcHeaderSize() {
    Dimension sizeFromHeader = myHeaderPanel.getPreferredSize();

    if (sizeFromHeader == null) {
      sizeFromHeader = myHeaderPanel.getMinimumSize();
    }

    if (sizeFromHeader == null) {
      int minimumSize = myWindow.getGraphics().getFontMetrics(myHeaderPanel.getFont()).getHeight();
      sizeFromHeader = new Dimension(minimumSize, minimumSize);
    }
    return sizeFromHeader;
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
      if (mySpeedSearchFoundInRootComponent != null && mySpeedSearchFoundInRootComponent.isHoldingFilter()) {
        mySpeedSearchFoundInRootComponent.reset();
      }
      else {
        cancel(e);
      }
      return true;
    }
    return false;
  }

  public @NotNull Dimension getHeaderPreferredSize() {
    return myHeaderPanel.getPreferredSize();
  }
  public @NotNull Dimension getFooterPreferredSize() {
    return myAdComponent == null ? new Dimension(0,0) : myAdComponent.getPreferredSize();
  }

  public static boolean isCloseRequest(KeyEvent e) {
    if (e != null && e.getID() == KeyEvent.KEY_PRESSED) {
      KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        Shortcut[] shortcuts = keymapManager.getActiveKeymap().getShortcuts(IdeActions.ACTION_EDITOR_ESCAPE);
        for (Shortcut shortcut : shortcuts) {
          if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
            if (keyboardShortcut.getFirstKeyStroke().getKeyCode() == e.getKeyCode() &&
                keyboardShortcut.getSecondKeyStroke() == null) {
              int m1 = keyboardShortcut.getFirstKeyStroke().getModifiers() & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK | InputEvent.ALT_MASK);
              int m2 = e.getModifiers();
              return m1 == m2;
            }
          }
        }
      }
      return e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0;
    }
    return false;
  }

  private @NotNull Point fixLocateByContent(@NotNull Point location, boolean save) {
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

  private static @Nullable Point getLocationOnScreen(@Nullable Component component) {
    return component == null || !component.isShowing() ? null : component.getLocationOnScreen();
  }

  private static @Nullable Rectangle getBoundsOnScreen(@Nullable Component component) {
    Point point = getLocationOnScreen(component);
    return point == null ? null : new Rectangle(point, component.getSize());
  }

  public static @NotNull List<JBPopup> getChildPopups(final @NotNull Component component) {
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

  @Override
  public @NotNull Rectangle getConsumedScreenBounds() {
    return myWindow.getBounds();
  }

  @Override
  public Window getUnderlyingWindow() {
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
   * Tells whether popup should be closed when some window becomes activated/focused
   */
  private boolean isCancelNeeded(@NotNull WindowEvent event, @Nullable Window popup) {
    Window window = event.getWindow(); // the activated or focused window
    return window == null ||
           popup == null ||
           !SwingUtilities.isDescendingFrom(window, popup) && (myFocusable || !SwingUtilities.isDescendingFrom(popup, window));
  }

  private @Nullable Point getStoredLocation() {
    if (myDimensionServiceKey == null) return null;
    return getWindowStateService(getProjectDependingOnKey(myDimensionServiceKey)).getLocation(myDimensionServiceKey);
  }

  private @Nullable Dimension getStoredSize() {
    if (myDimensionServiceKey == null) return null;
    return getWindowStateService(getProjectDependingOnKey(myDimensionServiceKey)).getSize(myDimensionServiceKey);
  }

  private static @NotNull WindowStateService getWindowStateService(@Nullable Project project) {
    return project == null ? WindowStateService.getInstance() : WindowStateService.getInstance(project);
  }

  private static <T> T findInComponentHierarchy(@NotNull Component component, Function<? super @NotNull Component, ? extends @Nullable T> mapper) {
    T found = mapper.fun(component);
    if (found != null) {
      return found;
    }
    if (component instanceof JComponent) {
      for (Component child : ((JComponent)component).getComponents()) {
        found = findInComponentHierarchy(child, mapper);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  private @Nullable JScrollBar findHorizontalScrollBar() {
    JScrollPane pane = ScrollUtil.findScrollPane(myContent);
    if (pane == null || ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER == pane.getHorizontalScrollBarPolicy()) return null;
    return pane.getHorizontalScrollBar();
  }

  private void forHorizontalScrollBar(@NotNull Consumer<? super JScrollBar> consumer) {
    JScrollBar bar = findHorizontalScrollBar();
    if (bar != null) consumer.consume(bar);
  }
}
