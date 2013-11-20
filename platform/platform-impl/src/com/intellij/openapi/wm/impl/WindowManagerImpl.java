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
package com.intellij.openapi.wm.impl;

import com.intellij.Patches;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManagerListener;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.platform.WindowUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
@State(
  name = "WindowManager",
  roamingType = RoamingType.GLOBAL,
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/window.manager.xml")})
public final class WindowManagerImpl extends WindowManagerEx implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.WindowManagerImpl");

  @NonNls public static final String FULL_SCREEN = "ide.frame.full.screen";

  @NonNls private static final String FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow";
  @NonNls private static final String X_ATTR = "x";
  @NonNls private static final String FRAME_ELEMENT = "frame";
  @NonNls private static final String Y_ATTR = "y";
  @NonNls private static final String WIDTH_ATTR = "width";
  @NonNls private static final String HEIGHT_ATTR = "height";
  @NonNls private static final String EXTENDED_STATE_ATTR = "extended-state";

  static {
    try {
      System.loadLibrary("jawt");
    }
    catch (Throwable t) {
      LOG.info("jawt failed to load", t);
    }
  }

  private static final boolean ORACLE_BUG_8007219 = SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.7");
  private static final int ORACLE_BUG_8007219_THRESHOLD = 10;

  private Boolean myAlphaModeSupported = null;

  private final EventDispatcher<WindowManagerListener> myEventDispatcher = EventDispatcher.create(WindowManagerListener.class);

  /**
   * Union of bounds of all available default screen devices.
   */
  private final Rectangle myScreenBounds;

  private final CommandProcessor myCommandProcessor;
  private final WindowWatcher myWindowWatcher;
  /**
   * That is the default layout.
   */
  private final DesktopLayout myLayout;

  private final HashMap<Project, IdeFrameImpl> myProject2Frame;

  private final HashMap<Project, Set<JDialog>> myDialogsToDispose;

  /**
   * This members is needed to read frame's bounds from XML.
   * <code>myFrameBounds</code> can be <code>null</code>.
   */
  private Rectangle myFrameBounds;
  private int myFrameExtendedState;
  private final WindowAdapter myActivationListener;
  private final ApplicationInfoEx myApplicationInfoEx;
  private final DataManager myDataManager;
  private final ActionManagerEx myActionManager;
  private final UISettings myUiSettings;

  /**
   * invoked by reflection
   * @param dataManager
   * @param applicationInfoEx
   * @param actionManager
   * @param uiSettings
   */
  public WindowManagerImpl(DataManager dataManager,
                           ApplicationInfoEx applicationInfoEx,
                           ActionManagerEx actionManager,
                           UISettings uiSettings,
                           MessageBus bus) {
    myApplicationInfoEx = applicationInfoEx;
    myDataManager = dataManager;
    myActionManager = actionManager;
    myUiSettings = uiSettings;
    if (myDataManager instanceof DataManagerImpl) {
        ((DataManagerImpl)myDataManager).setWindowManager(this);
    }

    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      Disposer.register(application, new Disposable() {
        @Override
        public void dispose() {
          disposeRootFrame();
        }
      });
    }

    myCommandProcessor = new CommandProcessor();
    myWindowWatcher = new WindowWatcher();
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, myWindowWatcher);
    if (Patches.SUN_BUG_ID_4218084) {
      keyboardFocusManager.addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, new SUN_BUG_ID_4218084_Patch());
    }
    myLayout = new DesktopLayout();
    myProject2Frame = new HashMap<Project, IdeFrameImpl>();
    myDialogsToDispose = new HashMap<Project, Set<JDialog>>();
    myFrameExtendedState = Frame.NORMAL;

    // Calculate screen bounds.

    Rectangle screenBounds = new Rectangle();
    if (!application.isHeadlessEnvironment()) {
      final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      final GraphicsDevice[] devices = env.getScreenDevices();
      for (final GraphicsDevice device : devices) {
        screenBounds = screenBounds.union(device.getDefaultConfiguration().getBounds());
      }
    }
    myScreenBounds = screenBounds;

    myActivationListener = new WindowAdapter() {
      public void windowActivated(WindowEvent e) {
        Window activeWindow = e.getWindow();
        if (activeWindow instanceof IdeFrameImpl) { // must be
          proceedDialogDisposalQueue(((IdeFrameImpl)activeWindow).getProject());
        }
      }
    };

    bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appClosing() {
        // save full screen window states
        if (isFullScreenSupportedInCurrentOS() && GeneralSettings.getInstance().isReopenLastProject()) {
          Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

          if (openProjects.length > 0) {
            WindowManagerEx wm = WindowManagerEx.getInstanceEx();
            for (Project project : openProjects) {
              IdeFrameImpl frame  = wm.getFrame(project);
              if (frame != null) {
                frame.storeFullScreenStateIfNeeded();
              }
            }
          }
        }
      }
    });

    if (UIUtil.hasLeakingAppleListeners()) {
      UIUtil.addAwtListener(new AWTEventListener() {
        @Override
        public void eventDispatched(AWTEvent event) {
          if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
            if (((ContainerEvent)event).getChild() instanceof JViewport) {
              UIUtil.removeLeakingAppleListeners();
            }
          }
        }
      }, AWTEvent.CONTAINER_EVENT_MASK, application);
    }
  }

  @NotNull
  public IdeFrameImpl[] getAllProjectFrames() {
    final Collection<IdeFrameImpl> ideFrames = myProject2Frame.values();
    return ideFrames.toArray(new IdeFrameImpl[ideFrames.size()]);
  }

  @Override
  public JFrame findVisibleFrame() {
    IdeFrameImpl[] frames = getAllProjectFrames();
    return frames.length > 0 ? frames[0] : (JFrame)WelcomeFrame.getInstance();
  }

  @Override
  public void addListener(final WindowManagerListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeListener(final WindowManagerListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public final Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  @Override
  public Rectangle getScreenBounds(@NotNull Project project) {
    final GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    final Point onScreen = getFrame(project).getLocationOnScreen();
    final GraphicsDevice[] devices = environment.getScreenDevices();
    for (final GraphicsDevice device : devices) {
      final Rectangle bounds = device.getDefaultConfiguration().getBounds();
      if (bounds.contains(onScreen)) {
        return bounds;
      }
    }

    return null;
  }

  public final boolean isInsideScreenBounds(final int x, final int y, final int width) {
    return
      x >= myScreenBounds.x + 50 - width &&
      y >= myScreenBounds.y - 50 &&
      x <= myScreenBounds.x + myScreenBounds.width - 50 &&
      y <= myScreenBounds.y + myScreenBounds.height - 50;
  }

  public final boolean isInsideScreenBounds(final int x, final int y) {
    return myScreenBounds.contains(x, y);
  }

  public final boolean isAlphaModeSupported() {
    if (myAlphaModeSupported == null) {
      myAlphaModeSupported = calcAlphaModelSupported();
    }
    return myAlphaModeSupported.booleanValue();
  }

  private static boolean calcAlphaModelSupported() {
    if (AWTUtilitiesWrapper.isTranslucencyAPISupported()) {
      return AWTUtilitiesWrapper.isTranslucencySupported(AWTUtilitiesWrapper.TRANSLUCENT);
    }
    try {
      return WindowUtils.isWindowAlphaSupported();
    }
    catch (Throwable e) {
      return false;
    }
  }

  public final void setAlphaModeRatio(final Window window, final float ratio) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    if (ratio < 0.0f || ratio > 1.0f) {
      throw new IllegalArgumentException("ratio must be in [0..1] range. ratio=" + ratio);
    }
    if (!isAlphaModeSupported() || !isAlphaModeEnabled(window)) {
      return;
    }


    setAlphaMode(window, ratio);
  }

  private static void setAlphaMode(Window window, float ratio) {
    try {
      if (SystemInfo.isMacOSLeopard) {
        if (window instanceof JWindow) {
          ((JWindow)window).getRootPane().putClientProperty("Window.alpha", 1.0f - ratio);
        } else if (window instanceof JDialog) {
          ((JDialog)window).getRootPane().putClientProperty("Window.alpha", 1.0f - ratio);
        } else if (window instanceof JFrame) {
          ((JFrame)window).getRootPane().putClientProperty("Window.alpha", 1.0f - ratio);
        }
      }
      else if (AWTUtilitiesWrapper.isTranslucencySupported(AWTUtilitiesWrapper.TRANSLUCENT)) {
        AWTUtilitiesWrapper.setWindowOpacity(window, 1.0f - ratio);
      }
      else {
        WindowUtils.setWindowAlpha(window, 1.0f - ratio);
      }
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
  }

  public void setWindowMask(final Window window, @Nullable final Shape mask) {
    try {
      if (AWTUtilitiesWrapper.isTranslucencySupported(AWTUtilitiesWrapper.PERPIXEL_TRANSPARENT)) {
        AWTUtilitiesWrapper.setWindowShape(window, mask);
      }
      else {
        WindowUtils.setWindowMask(window, mask);
      }
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
  }

  @Override
  public void setWindowShadow(Window window, WindowShadowMode mode) {
    if (window instanceof JWindow) {
      JRootPane root = ((JWindow)window).getRootPane();
      root.putClientProperty("Window.shadow", mode == WindowShadowMode.DISABLED ? Boolean.FALSE : Boolean.TRUE);
      root.putClientProperty("Window.style", mode == WindowShadowMode.SMALL ? "small" : null);
    }
  }

  public void resetWindow(final Window window) {
    try {
      if (!isAlphaModeSupported()) return;

      setWindowMask(window, null);
      setAlphaMode(window, 0f);
      setWindowShadow(window, WindowShadowMode.NORMAL);
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
  }

  public final boolean isAlphaModeEnabled(final Window window) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    return isAlphaModeSupported();
  }

  public final void setAlphaModeEnabled(final Window window, final boolean state) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
  }

  public void hideDialog(JDialog dialog, Project project) {
    if (project == null) {
      dialog.dispose();
    }
    else {
      IdeFrameImpl frame = getFrame(project);
      if (frame.isActive()) {
        dialog.dispose();
      }
      else {
        queueForDisposal(dialog, project);
        dialog.setVisible(false);
      }
    }
  }

  @Override
  public void adjustContainerWindow(Component c, Dimension oldSize, Dimension newSize) {
    if (c == null) return;

    Window wnd = SwingUtilities.getWindowAncestor(c);

    if (wnd instanceof JWindow) {
      JBPopup popup = (JBPopup)((JWindow)wnd).getRootPane().getClientProperty(JBPopup.KEY);
      if (popup != null) {
        if (oldSize.height < newSize.height) {
          Dimension size = popup.getSize();
          size.height += (newSize.height - oldSize.height);
          popup.setSize(size);
          popup.moveToFitScreen();
        }
      }
    }
  }

  public final void disposeComponent() {}

  public final void initComponent() {
  }

  public final void doNotSuggestAsParent(final Window window) {
    myWindowWatcher.doNotSuggestAsParent(window);
  }

  public final void dispatchComponentEvent(final ComponentEvent e) {
    myWindowWatcher.dispatchComponentEvent(e);
  }

  @Nullable
  public final Window suggestParentWindow(@Nullable final Project project) {
    return myWindowWatcher.suggestParentWindow(project);
  }

  @Nullable
  public final StatusBar getStatusBar(final Project project) {
    if (!myProject2Frame.containsKey(project)) {
      return null;
    }
    final IdeFrameImpl frame = getFrame(project);
    LOG.assertTrue(frame != null);
    return frame.getStatusBar();
  }

  @Override
  public StatusBar getStatusBar(@NotNull Component c) {
    return getStatusBar(c, null);
  }

  @Override
  public StatusBar getStatusBar(@NotNull Component c, @Nullable Project project) {
    Component parent = UIUtil.findUltimateParent(c);
    if (parent instanceof IdeFrame) {
      return ((IdeFrame)parent).getStatusBar().findChild(c);
    }

    IdeFrame frame = findFrameFor(project);
    if (frame != null) {
      return frame.getStatusBar().findChild(c);
    }

    assert false : "Cannot find status bar for " + c;

    return null;
  }

  public IdeFrame findFrameFor(@Nullable final Project project) {
    IdeFrame frame = null;
    if (project != null) {
      frame = project.isDefault() ? WelcomeFrame.getInstance() : getFrame(project);
    }
    else {
      Container eachParent = getMostRecentFocusedWindow();
      while(eachParent != null) {
        if (eachParent instanceof IdeFrame) {

          frame = (IdeFrame)eachParent;
          break;
        }
        eachParent = eachParent.getParent();
      }

      if (frame == null) {
        frame = tryToFindTheOnlyFrame();
      }
    }

    return frame;
  }

  private static IdeFrame tryToFindTheOnlyFrame() {
    IdeFrame candidate = null;
    final Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each instanceof IdeFrame) {
        if (candidate == null) {
          candidate = (IdeFrame)each;
        } else {
          candidate = null;
          break;
        }
      }
    }
    return candidate;
  }

  public final IdeFrameImpl getFrame(@Nullable final Project project) {
    // no assert! otherwise WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    return myProject2Frame.get(project);
  }

  public IdeFrame getIdeFrame(@Nullable final Project project) {
    if (project != null) {
      return getFrame(project);
    }
    final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    final Component parent = UIUtil.findUltimateParent(window);
    if (parent instanceof IdeFrame) return (IdeFrame)parent;

    final Frame[] frames = Frame.getFrames();
    for (Frame each : frames) {
      if (each instanceof IdeFrame) {
        return (IdeFrame)each;
      }
    }

    return null;
  }

  public void showFrame() {
    final IdeFrameImpl frame = new IdeFrameImpl(myApplicationInfoEx,
                                                myActionManager, myUiSettings, myDataManager,
                                                ApplicationManager.getApplication());
    myProject2Frame.put(null, frame);

    if (myFrameBounds == null || !ScreenUtil.isVisible(myFrameBounds)) { //avoid situations when IdeFrame is out of all screens
      final Rectangle rect = ScreenUtil.getMainScreenBounds();
      int yParts = rect.height / 6;
      int xParts = rect.width / 5;
      myFrameBounds = new Rectangle(xParts, yParts, xParts * 3, yParts * 4);
    }

    fixForOracleBug8007219(frame);

    frame.setBounds(myFrameBounds);
    frame.setExtendedState(myFrameExtendedState);
    frame.setVisible(true);

  }

  private void fixForOracleBug8007219(IdeFrameImpl frame) {
    if ((myFrameExtendedState & Frame.MAXIMIZED_BOTH) > 0 && ORACLE_BUG_8007219) {
      final Rectangle rect = ScreenUtil.getMainScreenBounds();
      final Insets screenInsets = ScreenUtil.getScreenInsets(frame.getGraphicsConfiguration());


      myFrameBounds.x = myFrameBounds.x - screenInsets.left > ORACLE_BUG_8007219_THRESHOLD ?
                        myFrameBounds.x :
                        screenInsets.left + ORACLE_BUG_8007219_THRESHOLD + 1;

      myFrameBounds.y = myFrameBounds.y - screenInsets.top > ORACLE_BUG_8007219_THRESHOLD ?
                        myFrameBounds.y :
                        screenInsets.top + ORACLE_BUG_8007219_THRESHOLD + 1;

      myFrameBounds.width = rect.width - (myFrameBounds.width + myFrameBounds.x) > ORACLE_BUG_8007219_THRESHOLD ?
                            myFrameBounds.width :
                            rect.width - ORACLE_BUG_8007219_THRESHOLD - 1;

      myFrameBounds.height = rect.height - (myFrameBounds.height + myFrameBounds.y) > ORACLE_BUG_8007219_THRESHOLD ?
                             myFrameBounds.height :
                             rect.height - ORACLE_BUG_8007219_THRESHOLD - 1;
    }
  }

  public final IdeFrameImpl allocateFrame(final Project project) {
    LOG.assertTrue(!myProject2Frame.containsKey(project));

    final IdeFrameImpl frame;
    if (myProject2Frame.containsKey(null)) {
      frame = myProject2Frame.get(null);
      myProject2Frame.remove(null);
      myProject2Frame.put(project, frame);
      frame.setProject(project);
    }
    else {
      frame = new IdeFrameImpl((ApplicationInfoEx)ApplicationInfo.getInstance(), ActionManagerEx.getInstanceEx(), UISettings.getInstance(),
                               DataManager.getInstance(), ApplicationManager.getApplication());

      final Rectangle bounds = ProjectFrameBounds.getInstance(project).getBounds();

      if (bounds != null) {
        myFrameBounds = bounds;
      }

      if (myFrameBounds != null) {
        fixForOracleBug8007219(frame);
        frame.setBounds(myFrameBounds);
      }
      frame.setProject(project);
      myProject2Frame.put(project, frame);
      frame.setExtendedState(myFrameExtendedState);
      frame.setVisible(true);
    }

    frame.addWindowListener(myActivationListener);

    myEventDispatcher.getMulticaster().frameCreated(frame);

    return frame;
  }

  private void proceedDialogDisposalQueue(Project project) {
    Set<JDialog> dialogs = myDialogsToDispose.get(project);
    if (dialogs == null) return;
    for (JDialog dialog : dialogs) {
      dialog.dispose();
    }
    myDialogsToDispose.put(project, null);
  }

  private void queueForDisposal(JDialog dialog, Project project) {
    Set<JDialog> dialogs = myDialogsToDispose.get(project);
    if (dialogs == null) {
      dialogs = new HashSet<JDialog>();
      myDialogsToDispose.put(project, dialogs);
    }
    dialogs.add(dialog);
  }

  public final void releaseFrame(final IdeFrameImpl frame) {

    myEventDispatcher.getMulticaster().beforeFrameReleased(frame);

    final Project project = frame.getProject();
    LOG.assertTrue(project != null);

    frame.removeWindowListener(myActivationListener);
    proceedDialogDisposalQueue(project);

    frame.setProject(null);
    frame.setTitle(null);
    frame.setFileTitle(null, null);

    myProject2Frame.remove(project);
    if (myProject2Frame.isEmpty()) {
      myProject2Frame.put(null, frame);
    }
    else {
      Disposer.dispose(frame.getStatusBar());
      frame.dispose();
    }
  }

  public final void disposeRootFrame() {
    if (myProject2Frame.size() == 1) {
      final IdeFrameImpl rootFrame = myProject2Frame.remove(null);
      if (rootFrame != null) {
        // disposing last frame if quitting
        rootFrame.dispose();
      }
    }
  }

  public final Window getMostRecentFocusedWindow() {
    return myWindowWatcher.getFocusedWindow();
  }

  public final Component getFocusedComponent(@NotNull final Window window) {
    return myWindowWatcher.getFocusedComponent(window);
  }

  @Nullable
  public final Component getFocusedComponent(@Nullable final Project project) {
    return myWindowWatcher.getFocusedComponent(project);
  }

  /**
   * Private part
   */
  public final CommandProcessor getCommandProcessor() {
    return myCommandProcessor;
  }

  @Override
  public void loadState(Element state) {
    final Element frameElement = state.getChild(FRAME_ELEMENT);
    if (frameElement != null) {
      myFrameBounds = loadFrameBounds(frameElement);
      try {
        myFrameExtendedState = Integer.parseInt(frameElement.getAttributeValue(EXTENDED_STATE_ATTR));
        if ((myFrameExtendedState & Frame.ICONIFIED) > 0) {
          myFrameExtendedState = Frame.NORMAL;
        }
      }
      catch (NumberFormatException ignored) {
        myFrameExtendedState = Frame.NORMAL;
      }
    }

    final Element desktopElement = state.getChild(DesktopLayout.TAG);
    if (desktopElement != null) {
      myLayout.readExternal(desktopElement);
    }
  }

  private static Rectangle loadFrameBounds(final Element frameElement) {
    Rectangle bounds = new Rectangle();
    try {
      bounds.x = Integer.parseInt(frameElement.getAttributeValue(X_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.y = Integer.parseInt(frameElement.getAttributeValue(Y_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.width = Integer.parseInt(frameElement.getAttributeValue(WIDTH_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.height = Integer.parseInt(frameElement.getAttributeValue(HEIGHT_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    return bounds;
  }

  @Nullable
  @Override
  public Element getState() {
    Element frameState = getFrameState();
    if (frameState == null) {
      return null;
    }

    Element state = new Element("state");
    state.addContent(frameState);

    // Save default layout
    Element layoutElement = new Element(DesktopLayout.TAG);
    state.addContent(layoutElement);
    myLayout.writeExternal(layoutElement);
    return state;
  }

  private Element getFrameState() {
    // Save frame bounds
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length == 0) {
      return null;
    }

    Project project = projects[0];
    final IdeFrameImpl frame = getFrame(project);
    if (frame == null) {
      return null;
    }

    final Element frameElement = new Element(FRAME_ELEMENT);
    int extendedState = frame.getExtendedState();
    if (SystemInfo.isMacOSLion) {
      @SuppressWarnings("deprecation") ComponentPeer peer = frame.getPeer();
      if (peer instanceof FramePeer) {
        // frame.state is not updated by jdk so get it directly from peer
        extendedState = ((FramePeer)peer).getState();
      }
    }
    boolean isMaximized = extendedState == Frame.MAXIMIZED_BOTH ||
                          isFullScreenSupportedInCurrentOS() && frame.isInFullScreen();
    boolean usePreviousBounds = isMaximized &&
                                myFrameBounds != null &&
                                frame.getBounds().contains(new Point((int)myFrameBounds.getCenterX(), (int)myFrameBounds.getCenterY()));
    Rectangle rectangle = usePreviousBounds ? myFrameBounds : frame.getBounds();
    frameElement.setAttribute(X_ATTR, Integer.toString(rectangle.x));
    frameElement.setAttribute(Y_ATTR, Integer.toString(rectangle.y));
    frameElement.setAttribute(WIDTH_ATTR, Integer.toString(rectangle.width));
    frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(rectangle.height));

    if (!(frame.isInFullScreen() && SystemInfo.isAppleJvm)) {
      frameElement.setAttribute(EXTENDED_STATE_ATTR, Integer.toString(extendedState));
    }
    return frameElement;
  }

  public final DesktopLayout getLayout() {
    return myLayout;
  }

  public final void setLayout(final DesktopLayout layout) {
    myLayout.copyFrom(layout);
  }

  @NotNull
  public final String getComponentName() {
    return "WindowManager";
  }

  /**
   * We cannot clear selected menu path just by changing of focused window. Under Windows LAF
   * focused window changes sporadically when user clicks on menu item or sub-menu. The problem
   * is that all popups under Windows LAF always has native window ancestor. This window isn't
   * focusable but by mouse click focused window changes in this manner:
   * InitialFocusedWindow->null
   * null->InitialFocusedWindow
   * To fix this problem we use alarm to accumulate such focus events.
   */
  private static final class SUN_BUG_ID_4218084_Patch implements PropertyChangeListener {
    private final Alarm myAlarm;
    private Window myInitialFocusedWindow;
    private Window myLastFocusedWindow;
    private final Runnable myClearSelectedPathRunnable;

    public SUN_BUG_ID_4218084_Patch() {
      myAlarm = new Alarm();
      myClearSelectedPathRunnable = new Runnable() {
        public void run() {
          if (myInitialFocusedWindow != myLastFocusedWindow) {
            MenuSelectionManager.defaultManager().clearSelectedPath();
          }
        }
      };
    }

    public void propertyChange(final PropertyChangeEvent e) {
      if (myAlarm.getActiveRequestCount() == 0) {
        myInitialFocusedWindow = (Window)e.getOldValue();
        final MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
        if (selectedPath.length == 0) { // there is no visible popup
          return;
        }
        Component firstComponent = null;
        for (final MenuElement menuElement : selectedPath) {
          final Component component = menuElement.getComponent();
          if (component instanceof JMenuBar) {
            firstComponent = component;
            break;
          } else if (component instanceof JPopupMenu) {
            firstComponent = ((JPopupMenu) component).getInvoker();
            break;
          }
        }
        if (firstComponent == null) {
          return;
        }
        final Window window = SwingUtilities.getWindowAncestor(firstComponent);
        if (window != myInitialFocusedWindow) { // focused window doesn't have popup
          return;
        }
      }
      myLastFocusedWindow = (Window)e.getNewValue();
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myClearSelectedPathRunnable, 150);
    }
  }

  public WindowWatcher getWindowWatcher() {
    return myWindowWatcher;
  }

  public boolean isFullScreenSupportedInCurrentOS() {
    return SystemInfo.isMacOSLion || SystemInfo.isWindows || SystemInfo.isXWindow && X11UiUtil.isFullScreenSupported();
  }

  public static boolean isFloatingMenuBarSupported() {
    return !SystemInfo.isMac && getInstance().isFullScreenSupportedInCurrentOS();
  }
}
