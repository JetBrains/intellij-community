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
package com.intellij.ui.popup;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.JBAwtEventQueue;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.impl.ShadowBorderPainter;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ImageLoader;
import com.intellij.util.Processor;
import com.intellij.util.ui.ChildFocusWatcher;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.ui.impl.ShadowBorderPainter.*;

public class AbstractPopup implements JBPopup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.AbstractPopup");

  private static final Image ourMacCorner = ImageLoader.loadFromResource("/general/macCorner.png");

  private PopupComponent myPopup;
  private MyContentPanel myContent;
  private JComponent myPreferredFocusedComponent;
  private boolean myRequestFocus;
  private boolean myFocusable;
  private boolean myForcedHeavyweight = false;
  private boolean myLocateWithinScreen = true;
  private boolean myResizable = false;
  private JPanel myHeaderPanel;
  private CaptionPanel myCaption = null;
  private JComponent myComponent;
  private String myDimensionServiceKey = null;
  private Computable<Boolean> myCallBack = null;
  private Project myProject;
  private boolean myCancelOnClickOutside;
  private Set<JBPopupListener> myListeners;
  private boolean myUseDimServiceForXYLocation;
  private MouseChecker myCancelOnMouseOutCallback;
  private Canceller myMouseOutCanceller;
  private boolean myCancelOnWindow;
  private Dimension myForcedSize;
  private Point myForcedLocation;
  private ChildFocusWatcher myFocusWatcher;
  private boolean myCancelKeyEnabled;
  private boolean myLocateByContent;
  protected FocusTrackback myFocusTrackback;
  private Dimension myMinSize;
  private ArrayList<Object> myUserData;
  private boolean myShadowed;
  private boolean myPaintShadow;

  private float myAlpha = 0;
  private float myLastAlpha = 0;

  private MaskProvider myMaskProvider;

  private Window myWindow;
  private boolean myInStack;
  private MyWindowListener myWindowListener;

  private boolean myModalContext;

  private Component[] myFocusOwners;
  private PopupBorder myPopupBorder;
  private Dimension myRestoreWindowSize;
  protected Component myOwner;
  protected Component myRequestorComponent;
  private boolean myHeaderAlwaysFocusable;
  private boolean myMovable;
  private JComponent myHeaderComponent;

  protected InputEvent myDisposeEvent;

  private Runnable myFinalRunnable;

  protected boolean myOk;

  protected final SpeedSearch mySpeedSearch = new SpeedSearch() {
    boolean searchFieldShown = false;

    protected void update() {
      mySpeedSearchPatternField.setBackground(new JTextField().getBackground());
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
      mySpeedSearchPatternField.setBackground(LightColors.RED);
    }
  };

  private JTextField mySpeedSearchPatternField;
  private boolean myNativePopup;
  private boolean myMayBeParent;


  AbstractPopup() {
  }

  AbstractPopup init(final Project project,
                     @NotNull final JComponent component,
                     @Nullable final JComponent preferredFocusedComponent,
                     final boolean requestFocus,
                     final boolean focusable,
                     final boolean forceHeavyweight,
                     final boolean movable,
                     final String dimensionServiceKey,
                     final boolean resizable,
                     @Nullable final String caption,
                     @Nullable final Computable<Boolean> callback,
                     final boolean cancelOnClickOutside,
                     @Nullable final Set<JBPopupListener> listeners,
                     final boolean useDimServiceForXYLocation,
                     InplaceButton commandButton,
                     @Nullable final IconButton cancelButton,
                     @Nullable final MouseChecker cancelOnMouseOutCallback,
                     final boolean cancelOnWindow,
                     @Nullable final ActiveIcon titleIcon,
                     final boolean cancelKeyEnabled,
                     final boolean locateBycontent,
                     final boolean placeWithinScreenBounds,
                     @Nullable final Dimension minSize,
                     float alpha,
                     @Nullable MaskProvider maskProvider,
                     boolean inStack,
                     boolean modalContext,
                     @Nullable Component[] focusOwners,
                     @Nullable String adText,
                     final boolean headerAlwaysFocusable,
                     @NotNull List<Pair<ActionListener, KeyStroke>> keyboardActions,
                     Component settingsButtons,
                     @Nullable final Processor<JBPopup> pinCallback,
                     boolean mayBeParent,
                     boolean showShadow) {

    if (requestFocus && !focusable) {
      assert false : "Incorrect argument combination: requestFocus=" + requestFocus + " focusable=" + focusable;
    }

    myProject = project;
    myComponent = component;
    myPopupBorder = PopupBorder.Factory.create(true);
    myShadowed = showShadow;
    myPaintShadow = showShadow && !SystemInfo.isMac && !movable && !resizable && Registry.is("ide.popup.dropShadow");
    myContent = createContentPanel(resizable, myPopupBorder, isToDrawMacCorner());
    myMayBeParent = mayBeParent;

    myContent.add(component, BorderLayout.CENTER);
    if (adText != null) {
      myContent.add(HintUtil.createAdComponent(adText), BorderLayout.SOUTH);
    }

    myCancelKeyEnabled = cancelKeyEnabled;
    myLocateByContent = locateBycontent;
    myLocateWithinScreen = placeWithinScreenBounds;
    myAlpha = alpha;
    myMaskProvider = maskProvider;
    myInStack = inStack;
    myModalContext = modalContext;
    myFocusOwners = focusOwners;
    myHeaderAlwaysFocusable = headerAlwaysFocusable;
    myMovable = movable;

    ActiveIcon actualIcon = titleIcon == null ? new ActiveIcon(new EmptyIcon(0)) : titleIcon;

    myHeaderPanel = new JPanel(new BorderLayout());

    if (caption != null) {
      if (caption.length() > 0) {
        myCaption = new TitlePanel(actualIcon.getRegular(), actualIcon.getInactive());
        ((TitlePanel)myCaption).setText(caption);
      }
      else {
        myCaption = new CaptionPanel();
      }

      if (pinCallback != null) {
        myCaption.setButtonComponent(new InplaceButton(new IconButton("Pin", IconLoader.getIcon("/general/autohideOff.png"),
                                                                      IconLoader.getIcon("/general/autohideOff.png"),
                                                                      IconLoader.getIcon("/general/autohideOffInactive.png")),
                                                       new ActionListener() {
                                                         public void actionPerformed(final ActionEvent e) {
                                                           pinCallback.process(AbstractPopup.this);
                                                         }
                                                       }));
      }
      else if (cancelButton != null) {
        myCaption.setButtonComponent(new InplaceButton(cancelButton, new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            cancel();
          }
        }));
      }
      else if (commandButton != null) {
        myCaption.setButtonComponent(commandButton);
      }
    }
    else {
      myCaption = new CaptionPanel();
      myCaption.setBorder(null);
      myCaption.setPreferredSize(new Dimension(0, 0));
    }

    setWindowActive(myHeaderAlwaysFocusable);

    myHeaderPanel.add(myCaption, BorderLayout.NORTH);
    myContent.add(myHeaderPanel, BorderLayout.NORTH);

    myForcedHeavyweight = forceHeavyweight;
    myResizable = resizable;
    myPreferredFocusedComponent = preferredFocusedComponent;
    myRequestFocus = requestFocus;
    myFocusable = focusable;
    myDimensionServiceKey = dimensionServiceKey;
    myCallBack = callback;
    myCancelOnClickOutside = cancelOnClickOutside;
    myCancelOnMouseOutCallback = cancelOnMouseOutCallback;
    myListeners = listeners == null ? new HashSet<JBPopupListener>() : listeners;
    myUseDimServiceForXYLocation = useDimServiceForXYLocation;
    myCancelOnWindow = cancelOnWindow;
    myMinSize = minSize;

    for (Pair<ActionListener, KeyStroke> pair : keyboardActions) {
      myContent.registerKeyboardAction(pair.getFirst(), pair.getSecond(), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    if (settingsButtons != null) {
      myCaption.addSettingsComponent(settingsButtons);
    }

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
    return new MyContentPanel(resizable, border, isToDrawMacCorner, myPaintShadow);
  }

  public static boolean isToDrawMacCorner() {
    return SystemInfo.isMac;
  }


  public String getDimensionServiceKey() {
    return myDimensionServiceKey;
  }

  public void setDimensionServiceKey(final String dimensionServiceKey) {
    myDimensionServiceKey = dimensionServiceKey;
  }

  public void showInCenterOf(@NotNull Component aContainer) {
    final Point popupPoint = getCenterOf(aContainer, myContent);
    show(aContainer, popupPoint.x, popupPoint.y, false);
  }

  public void setAdText(@NotNull final String s) {
    myContent.add(HintUtil.createAdComponent(s, BorderFactory.createEmptyBorder(3, 5, 3, 5)), BorderLayout.SOUTH);
  }

  public static Point getCenterOf(final Component aContainer, final JComponent content) {
    final JComponent component = getTargetComponent(aContainer);

    Point containerScreenPoint = component.getVisibleRect().getLocation();
    SwingUtilities.convertPointToScreen(containerScreenPoint, aContainer);

    return UIUtil.getCenterPoint(new Rectangle(containerScreenPoint, component.getVisibleRect().getSize()), content.getPreferredSize());
  }

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

    if (window != null) {
      showInCenterOf(window);
    }
  }

  public void showUnderneathOf(@NotNull Component aComponent) {
    show(new RelativePoint(aComponent, new Point(0, aComponent.getHeight())));
  }

  public void show(@NotNull RelativePoint aPoint) {
    final Point screenPoint = aPoint.getScreenPoint();
    show(aPoint.getComponent(), screenPoint.x, screenPoint.y, false);
  }

  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {
    show(owner, point.x, point.y, false);
  }

  public void showInBestPositionFor(@NotNull DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      showInBestPositionFor(editor);
    }
    else {
      show(relativePointByQuickSearch(dataContext));
    }
  }

  public void showInFocusCenter() {
    final Component focused = getWndManager().getFocusedComponent(myProject);
    if (focused != null) {
      showInCenterOf(focused);
    }
    else {
      final JFrame frame = WindowManager.getInstance().getFrame(myProject);
      showInCenterOf(frame.getRootPane());
    }
  }

  private RelativePoint relativePointByQuickSearch(final DataContext dataContext) {
    Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(dataContext);

    if (dominantArea != null) {
      final Component focusedComponent = getWndManager().getFocusedComponent(myProject);
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

    return JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
  }

  public void showInBestPositionFor(@NotNull Editor editor) {
    assert editor.getComponent().isShowing() : "Editor must be showing on the screen";

    DataContext context = ((EditorEx)editor).getDataContext();
    Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(context);
    if (dominantArea != null && !myRequestFocus) {
      final JLayeredPane layeredPane = editor.getContentComponent().getRootPane().getLayeredPane();
      show(relativePointWithDominantRectangle(layeredPane, dominantArea));
    }
    else {
      show(JBPopupFactory.getInstance().guessBestPopupLocation(editor));
    }
  }

  public void addPopupListener(JBPopupListener listener) {
    myListeners.add(listener);
  }

  private RelativePoint relativePointWithDominantRectangle(final JLayeredPane layeredPane, final Rectangle bounds) {
    Dimension preferredSize = getComponent().getPreferredSize();
    if (myDimensionServiceKey != null) {
      final Dimension dimension = DimensionService.getInstance().getSize(myDimensionServiceKey, myProject);
      if (dimension != null) {
        preferredSize = dimension;
      }
    }
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
        final int spaceOnTheRight = (screen.x + screen.width) - leftTopCornerScreen.x;
        if (spaceOnTheLeft > spaceOnTheRight) {
          relativePoint = new RelativePoint(layeredPane, new Point(0, bounds.y));
          myComponent.setPreferredSize(new Dimension(spaceOnTheLeft, Math.max(preferredSize.height, 200)));
        }
        else {
          relativePoint = new RelativePoint(layeredPane, leftTopCorner);
          myComponent.setPreferredSize(new Dimension(spaceOnTheRight, Math.max(preferredSize.height, 200)));
        }
      }
    }
    return relativePoint;
  }

  public final void closeOk(@Nullable InputEvent e) {
    setOk(true);
    cancel(e);
  }

  public final void cancel() {
    cancel(null);
  }

  public void setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  public void cancel(InputEvent e) {
    if (isDisposed()) return;

    if (myPopup != null) {
      if (!canClose()) {
        return;
      }
      storeDimensionSize(myContent.getSize());
      if (myUseDimServiceForXYLocation) {
        final JRootPane root = myComponent.getRootPane();
        if (root != null) {
          final Container popupWindow = root.getParent();
          if (popupWindow != null && popupWindow.isShowing()) {
            storeLocation(popupWindow.getLocationOnScreen());
          }
        }
      }

      if (e instanceof MouseEvent) {
        JBAwtEventQueue.getInstance().blockNextEvents(((MouseEvent)e));
      }

      myPopup.hide(false);

      if (ApplicationManagerEx.getApplicationEx() != null) {
        StackingPopupDispatcher.getInstance().onPopupHidden(this);
      }

      if (myInStack) {
        myFocusTrackback.restoreFocus();
      }


      disposePopup();

      if (myListeners != null) {
        for (JBPopupListener each : myListeners) {
          each.onClosed(new LightweightWindowEvent(this, myOk));
        }
      }
    }

    Disposer.dispose(this, false);
  }


  private void disposePopup() {
    if (myPopup != null) {
      myPopup.hide(true);
    }
    myPopup = null;
  }

  public boolean canClose() {
    return myCallBack == null || myCallBack.compute().booleanValue();
  }

  public boolean isVisible() {
    return myPopup != null;
  }

  public void show(final Component owner) {
    show(owner, -1, -1, true);
  }

  public void show(Component owner, int aScreenX, int aScreenY, final boolean considerForcedXY) {
    if (ApplicationManagerEx.getApplicationEx() != null && ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (isDisposed()) {
      throw new IllegalStateException("Popup was already disposed. Recreate a new instance to show again");
    }

    assert ApplicationManager.getApplication().isDispatchThread();


    final boolean shouldShow = beforeShow();
    if (!shouldShow) {
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

    if (myMinSize == null) {
      myMinSize = myContent.getMinimumSize();
    }

    if (sizeToSet == null) {
      sizeToSet = myContent.getPreferredSize();
    }

    if (sizeToSet != null) {
      sizeToSet.width = Math.max(sizeToSet.width, myMinSize.width);
      sizeToSet.height = Math.max(sizeToSet.height, myMinSize.height);

      myContent.setSize(sizeToSet);
      myContent.setPreferredSize(sizeToSet);
    }

    Point xy = new Point(aScreenX, aScreenY);
    boolean adjustXY = true;
    if (myDimensionServiceKey != null) {
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

    if (myLocateByContent) {
      final Dimension captionSize = myHeaderPanel.getPreferredSize();
      xy.y -= captionSize.height;
    }

    Rectangle targetBounds = new Rectangle(xy, myContent.getPreferredSize());
    Rectangle original = new Rectangle(targetBounds);
    if (myLocateWithinScreen) {
      ScreenUtil.moveRectangleToFitTheScreen(targetBounds);
    }

    if (myMouseOutCanceller != null) {
      myMouseOutCanceller.myEverEntered = targetBounds.equals(original);
    }

    myOwner = IdeFrameImpl.findNearestModalComponent(owner);
    if (myOwner == null) {
      myOwner = owner;
    }

    myRequestorComponent = owner;

    PopupComponent.Factory factory = getFactory(myForcedHeavyweight || myResizable);
    myNativePopup = factory.isNativePopup();
    myPopup = factory.getPopup(myOwner, myContent, targetBounds.x, targetBounds.y);

    if (myResizable) {
      final JRootPane root = myContent.getRootPane();
      final IdeGlassPaneImpl glass = new IdeGlassPaneImpl(root);
      root.setGlassPane(glass);

      final ResizeComponentListener resizeListener = new ResizeComponentListener(this);
      glass.addMousePreprocessor(resizeListener, this);
      glass.addMouseMotionPreprocessor(resizeListener, this);
    }

    if (myCaption != null && myMovable) {
      final MoveComponentListener moveListener = new MoveComponentListener(myCaption) {
        public void mousePressed(final MouseEvent e) {
          super.mousePressed(e);
          if (e.isConsumed()) return;

          if (UIUtil.isCloseClick(e)) {
            if (myCaption.isWithinPanel(e)) {
              cancel();
            }
          }
        }
      };
      ListenerUtil.addMouseListener(myCaption, moveListener);
      ListenerUtil.addMouseMotionListener(myCaption, moveListener);
      final MyContentPanel saved = myContent;
      Disposer.register(this, new Disposable() {
        public void dispose() {
          ListenerUtil.removeMouseListener(saved, moveListener);
          ListenerUtil.removeMouseMotionListener(saved, moveListener);
        }
      });
    }

    for (JBPopupListener listener : myListeners) {
      listener.beforeShown(new LightweightWindowEvent(this));
    }

    Window w = myPopup.getWindow();
    if (w != null) {
      WindowManagerEx.WindowShadowMode mode =
        myShadowed ? WindowManagerEx.WindowShadowMode.NORMAL : WindowManagerEx.WindowShadowMode.DISABLED;
      WindowManagerEx.getInstanceEx().setWindowShadow(myWindow, mode);
    }

    myPopup.setRequestFocus(myRequestFocus);
    myPopup.show();

    final Window window = SwingUtilities.getWindowAncestor(myContent);

    myWindowListener = new MyWindowListener();
    window.addWindowListener(myWindowListener);

    if (myFocusable) {
      window.setFocusableWindowState(true);
      window.setFocusable(true);
      if (myRequestFocus) {
        window.requestFocusInWindow();
      }
    }

    myWindow = updateMaskAndAlpha(window);

    if (myWindow instanceof JWindow) {
      ((JWindow)myWindow).getRootPane().putClientProperty(KEY, this);
    }

    if (myWindow != null) {
      //todo[kirillk,nik] SwingUtilities.getWindowAncestor() sometimes returns IdeFrameImpl (at least on Linux) but IDEA shouldn't mark
      // IdeFrame as 'doNotSuggestAsParent' (otherwise some popups like Ctrl+N won't work)
      if (!(myWindow instanceof IdeFrame)) {
        if (!myMayBeParent) {
          WindowManager.getInstance().doNotSuggestAsParent(myWindow);
        }
      }
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isDisposed()) return;

        if (myRequestFocus) {
          requestFocus();
        }

        if (myPreferredFocusedComponent != null && myInStack) {
          myFocusTrackback.registerFocusComponent(myPreferredFocusedComponent);
        }

        afterShow();
      }
    });
  }

  private void prepareToShow() {
    final MouseAdapter mouseAdapter = new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        Point point = (Point)e.getPoint().clone();
        SwingUtilities.convertPointToScreen(point, e.getComponent());

        final Dimension dimension = myContent.getSize();
        dimension.height += myResizable && isToDrawMacCorner() ? ourMacCorner.getHeight(myContent) : 4;
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
      public void dispose() {
        myContent.removeMouseListener(mouseAdapter);
      }
    });

    myContent.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myCancelKeyEnabled) {
          cancel();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);


    myContent.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        mySpeedSearch.process(e);
      }

      public void keyPressed(final KeyEvent e) {
        mySpeedSearch.process(e);
      }

      public void keyReleased(final KeyEvent e) {
        mySpeedSearch.process(e);
      }
    });

    if (myCancelOnMouseOutCallback != null || myCancelOnWindow) {
      myMouseOutCanceller = new Canceller();
      Toolkit.getDefaultToolkit().addAWTEventListener(myMouseOutCanceller, AWTEvent.MOUSE_EVENT_MASK | WindowEvent.WINDOW_ACTIVATED |
                                                                           AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }


    myFocusWatcher = new ChildFocusWatcher(myContent) {
      protected void onFocusGained(final FocusEvent event) {
        setWindowActive(true);
      }

      protected void onFocusLost(final FocusEvent event) {
        setWindowActive(false);
      }

    };

    mySpeedSearchPatternField = new JTextField();
    if (SystemInfo.isMac) {
      Font f = mySpeedSearchPatternField.getFont();
      mySpeedSearchPatternField.setFont(f.deriveFont(f.getStyle(), f.getSize() - 2));
    }
  }

  private Window updateMaskAndAlpha(Window window) {
    if (window == null) return window;

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

    return window;
  }

  private static WindowManagerEx getWndManager() {
    return ApplicationManagerEx.getApplicationEx() != null ? WindowManagerEx.getInstanceEx() : null;
  }

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

  protected final void requestFocus() {
    if (!myFocusable) return;

    if (myPreferredFocusedComponent != null) {
      if (myProject != null) {
        getFocusManager().requestFocus(myPreferredFocusedComponent, true);
      }
      else {
        myPreferredFocusedComponent.requestFocus();
      }
    }
  }

  private IdeFocusManager getFocusManager() {
    if (myProject != null) {
      return IdeFocusManager.getInstance(myProject);
    }
    else if (myOwner != null) {
      return IdeFocusManager.findInstanceByComponent(myOwner);
    }
    else {
      return IdeFocusManager.findInstance();
    }
  }

  private static JComponent getTargetComponent(Component aComponent) {
    if (aComponent instanceof JComponent) {
      return (JComponent)aComponent;
    }
    else if (aComponent instanceof RootPaneContainer) {
      return ((RootPaneContainer)aComponent).getRootPane();
    }

    LOG.error("Cannot find target for:" + aComponent);
    return null;
  }

  private PopupComponent.Factory getFactory(boolean forceHeavyweight) {
    if (isPersistent()) {
      return new PopupComponent.Factory.Dialog();
    }
    else if (forceHeavyweight || !SystemInfo.isWindows) {
      return new PopupComponent.Factory.AwtHeavyweight();
    }
    else {
      return new PopupComponent.Factory.AwtDefault();
    }
  }

  public JComponent getContent() {
    return myContent;
  }

  public void setLocation(RelativePoint p) {
    setLocation(p, myPopup, myContent);
  }

  private static void setLocation(final RelativePoint p, final PopupComponent popup, Component content) {
    if (popup == null) return;

    final Window wnd = popup.getWindow();
    assert wnd != null;

    wnd.setLocation(p.getScreenPoint());
  }

  public void pack() {
    final Window window = SwingUtilities.getWindowAncestor(myContent);

    window.pack();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setProject(Project project) {
    myProject = project;
  }


  public void dispose() {
    Disposer.dispose(this, false);

    assert ApplicationManager.getApplication().isDispatchThread();

    if (myPopup != null) {
      cancel(myDisposeEvent);
    }

    if (myContent != null) {
      myContent.removeAll();
    }
    myContent = null;
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

    if (myFocusWatcher != null) {
      myFocusWatcher.dispose();
      myFocusWatcher = null;
    }

    resetWindow();

    if (myFinalRunnable != null) {
      getFocusManager().doWhenFocusSettlesDown(myFinalRunnable);
      myFinalRunnable = null;
    }
  }

  private void resetWindow() {
    if (myWindow != null && getWndManager() != null) {
      getWndManager().resetWindow(myWindow);
      if (myWindowListener != null) {
        myWindow.removeWindowListener(myWindowListener);
      }

      if (myWindow instanceof JWindow) {
        ((JWindow)myWindow).getRootPane().putClientProperty(KEY, null);
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

  public static class MyContentPanel extends JPanel {
    private final boolean myResizable;
    private final boolean myDrawMacCorner;
    private final boolean myPaintShadow;

    public MyContentPanel(final boolean resizable, final PopupBorder border, boolean drawMacCorner) {
      this(resizable, border, drawMacCorner, false);
    }

    public MyContentPanel(final boolean resizable, final PopupBorder border, boolean drawMacCorner, boolean shadowed) {
      super(new BorderLayout());
      myResizable = resizable;
      myDrawMacCorner = drawMacCorner;
      myPaintShadow = shadowed && !UISettings.isRemoteDesktopConnected();
      if (myPaintShadow) {
        setOpaque(false);
        setBorder(new EmptyBorder(POPUP_TOP_SIZE, POPUP_SIDE_SIZE, POPUP_BOTTOM_SIZE, POPUP_SIDE_SIZE) {
          @Override
          public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            border.paintBorder(c, g,
                               x + POPUP_SIDE_SIZE - 1,
                               y + POPUP_TOP_SIZE - 1,
                               width - 2 * POPUP_SIDE_SIZE + 2,
                               height - POPUP_TOP_SIZE - POPUP_BOTTOM_SIZE + 2);
          }
        });
      }
      else {
        setBorder(border);
      }
    }

    public void paint(Graphics g) {
      if (myPaintShadow) {
        paintShadow(g);
      }

      super.paint(g);

      if (myResizable && myDrawMacCorner) {
        g.drawImage(ourMacCorner,
                    getX() + getWidth() - ourMacCorner.getWidth(this),
                    getY() + getHeight() - ourMacCorner.getHeight(this),
                    this);
      }
    }

    private void paintShadow(final Graphics g) {
      BufferedImage capture = null;
      try {
        final Point onScreen = getLocationOnScreen();
        capture = new Robot().createScreenCapture(
          new Rectangle(onScreen.x, onScreen.y, getWidth() + 2 * POPUP_SIDE_SIZE, getHeight() + POPUP_TOP_SIZE + POPUP_BOTTOM_SIZE));
        final BufferedImage shadow = ShadowBorderPainter.createPopupShadow(this, getWidth(), getHeight());
        ((Graphics2D)capture.getGraphics()).drawImage(shadow, null, null);
      }
      catch (Exception e) {
        LOG.info(e);
      }
      if (capture != null) g.drawImage(capture, 0, 0, null);
    }
  }

  public boolean isCancelOnClickOutside() {
    return myCancelOnClickOutside;
  }

  private class Canceller implements AWTEventListener {

    private boolean myEverEntered = false;

    public void eventDispatched(final AWTEvent event) {
      if (event.getID() == WindowEvent.WINDOW_ACTIVATED) {
        if (myCancelOnWindow) {
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

  public void setLocation(@NotNull final Point screenPoint) {
    if (myPopup == null) {
      myForcedLocation = screenPoint;
    }
    else {
      moveTo(myContent, screenPoint, myLocateByContent ? myHeaderPanel.getPreferredSize() : null);
    }
  }

  public static Window moveTo(JComponent content, Point screenPoint, final Dimension headerCorrectionSize) {
    setDefaultCursor(content);
    final Window wnd = SwingUtilities.getWindowAncestor(content);
    if (headerCorrectionSize != null) {
      screenPoint.y -= headerCorrectionSize.height;
    }
    wnd.setLocation(screenPoint);
    return wnd;
  }

  public void setSize(@NotNull final Dimension size) {
    if (myPopup == null) {
      myForcedSize = size;
    }
    else {
      updateMaskAndAlpha(setSize(myContent, size));
    }
  }

  @Override
  public Dimension getSize() {
    if (myPopup != null) {
      final Window popupWindow = SwingUtilities.windowForComponent(myContent);
      return popupWindow.getSize();
    } else {
      return myForcedSize;
    }
  }

  @Override
  public void moveToFitScreen() {
    if (myPopup == null) return;
    
    final Window popupWindow = SwingUtilities.windowForComponent(myContent);
    Rectangle bounds = popupWindow.getBounds();

    ScreenUtil.moveRectangleToFitTheScreen(bounds);
    setLocation(bounds.getLocation());
    setSize(bounds.getSize());
  }


  public static Window setSize(JComponent content, final Dimension size) {
    final Window popupWindow = SwingUtilities.windowForComponent(content);
    final Point location = popupWindow.getLocation();
    popupWindow.setBounds(location.x, location.y, size.width, size.height);
    return popupWindow;
  }

  public static void setDefaultCursor(JComponent content) {
    final Window wnd = SwingUtilities.getWindowAncestor(content);
    if (wnd != null) {
      wnd.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void setCaption(String title) {
    if (myCaption instanceof TitlePanel) {
      ((TitlePanel)myCaption).setText(title);
    }
  }

  private class MyWindowListener extends WindowAdapter {
    public void windowClosed(final WindowEvent e) {
      resetWindow();
    }
  }

  public boolean isPersistent() {
    return !myCancelOnClickOutside && !myCancelOnWindow;
  }

  public boolean isNativePopup() {
    return myNativePopup;
  }

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

  private Window getPopupWindow() {
    return myPopup.getWindow();
  }

  public void setUserData(ArrayList<Object> userData) {
    myUserData = userData;
  }

  public <T> T getUserData(final Class<T> userDataClass) {
    if (myUserData != null) {
      for (Object o : myUserData) {
        if (userDataClass.isInstance(o)) {
          //noinspection unchecked
          return (T)o;
        }
      }
    }
    return null;
  }

  public boolean isModalContext() {
    return myModalContext;
  }

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
    for (Component each : components) {
      if (each != null && SwingUtilities.isDescendingFrom(owner, each)) return true;
    }

    return false;
  }

  public boolean isCancelKeyEnabled() {
    return myCancelKeyEnabled;
  }

  @NotNull
  CaptionPanel getTitle() {
    return myCaption;
  }

  private void setHeaderComponent(JComponent c) {
    boolean doRevalidate = false;
    if (myHeaderComponent != null) {
      myHeaderPanel.remove(myHeaderComponent);
      myHeaderPanel.add(myCaption, BorderLayout.NORTH);
      myHeaderComponent = null;
      doRevalidate = true;
    }

    if (c != null) {
      myHeaderPanel.remove(myCaption);
      myHeaderPanel.add(c, BorderLayout.NORTH);
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

  public void addListener(final JBPopupListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(final JBPopupListener listener) {
    myListeners.remove(listener);
  }

  protected void onSpeedSearchPatternChanged() {
  }

  public Component getOwner() {
    return myRequestorComponent;
  }

  public void setMinimumSize(Dimension size) {
    myMinSize = size;
  }

  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  public void setFinalRunnable(Runnable finalRunnable) {
    myFinalRunnable = finalRunnable;
  }

  public void setOk(boolean ok) {
    myOk = ok;
  }
}
