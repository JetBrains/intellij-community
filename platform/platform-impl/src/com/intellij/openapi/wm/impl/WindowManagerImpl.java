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
package com.intellij.openapi.wm.impl;

import com.intellij.*;
import com.intellij.ide.*;
import com.intellij.ide.impl.*;
import com.intellij.ide.ui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.keymap.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.*;
import com.intellij.util.*;
import com.intellij.util.ui.*;
import com.sun.jna.examples.*;
import org.jdom.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

public final class WindowManagerImpl extends WindowManagerEx implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.WindowManagerImpl");
  private static boolean ourAlphaModeLibraryLoaded;
  @NonNls private static final String FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow";
  @NonNls private static final String X_ATTR = "x";
  @NonNls private static final String FRAME_ELEMENT = "frame";
  @NonNls private static final String Y_ATTR = "y";
  @NonNls private static final String WIDTH_ATTR = "width";
  @NonNls private static final String HEIGHT_ATTR = "height";
  @NonNls private static final String EXTENDED_STATE_ATTR = "extended-state";
  private Boolean myAlphaModeSupported = null;

  private final EventDispatcher<WindowManagerListener> myEventDispatcher = EventDispatcher.create(WindowManagerListener.class);

  static {
    initialize();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initialize() {
    try {
      System.loadLibrary("jawt");
      ourAlphaModeLibraryLoaded = true;
    }
    catch (Throwable exc) {
      ourAlphaModeLibraryLoaded = false;
    }
  }

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
  private final ActionManager myActionManager;
  private final UISettings myUiSettings;
  private final KeymapManager myKeymapManager;

  /**
   * invoked by reflection
   * @param dataManager
   * @param applicationInfoEx
   * @param actionManager
   * @param uiSettings
   * @param keymapManager
   */
  public WindowManagerImpl(DataManager dataManager,
                              ApplicationInfoEx applicationInfoEx,
                              ActionManager actionManager,
                              UISettings uiSettings,
                              KeymapManager keymapManager) {
    myApplicationInfoEx = applicationInfoEx;
    myDataManager = dataManager;
    myActionManager = actionManager;
    myUiSettings = uiSettings;
    myKeymapManager = keymapManager;
    if (myDataManager instanceof DataManagerImpl) {
        ((DataManagerImpl)myDataManager).setWindowManager(this);
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
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
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
  }

  public void showFrame(final String[] args) {
    IdeEventQueue.getInstance().setWindowManager(this);
    final IdeFrameImpl frame = new IdeFrameImpl(myApplicationInfoEx, myActionManager, myUiSettings, myDataManager, myKeymapManager,
                                                ApplicationManager.getApplication(), args);
    myProject2Frame.put(null, frame);
    if (myFrameBounds != null) {
      frame.setBounds(myFrameBounds);
    }
    frame.setVisible(true);
    frame.setExtendedState(myFrameExtendedState);
  }

  public IdeFrameImpl[] getAllFrames() {
    final Collection<IdeFrameImpl> ideFrames = myProject2Frame.values();
    return ideFrames.toArray(new IdeFrameImpl[ideFrames.size()]);
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

  private void setAlphaMode(Window window, float ratio) {
    try {
      if (SystemInfo.isMacOSLeopard) {
        if (window instanceof JWindow) {
          ((JWindow)window).getRootPane().putClientProperty("Window.alpha", 1.0f - ratio);
        } else if (window instanceof JDialog) {
          ((JDialog)window).getRootPane().putClientProperty("Window.alpha", 1.0f - ratio);
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

  public void setWindowMask(final Window window, final Shape mask) {
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

  public void resetWindow(final Window window) {
    try {
      if (!isAlphaModeSupported()) return;

      WindowUtils.setWindowMask(window, (Shape)null);
      setAlphaMode(window, 0f);
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

  public final void disposeComponent() {}

  public final void initComponent() {
  }

  public final void doNotSuggestAsParent(final Window window) {
    myWindowWatcher.doNotSuggestAsParent(window);
  }

  public final void dispatchComponentEvent(final ComponentEvent e) {
    myWindowWatcher.dispatchComponentEvent(e);
  }

  public final Window suggestParentWindow(final Project project) {
    return myWindowWatcher.suggestParentWindow(project);
  }

  public final StatusBar getStatusBar(final Project project) {
    if (!myProject2Frame.containsKey(project)) {
      return null;
    }
    final IdeFrameImpl frame = getFrame(project);
    LOG.assertTrue(frame != null);
    return frame.getStatusBar();
  }

  public IdeFrame findFrameFor(@Nullable final Project project) {
    IdeFrameImpl frame = null;
    if (project != null) {
      frame =  getFrame(project);
    } else {
      Container eachParent = getMostRecentFocusedWindow();
      while(eachParent != null) {
        if (eachParent instanceof IdeFrameImpl) {
          frame = (IdeFrameImpl)eachParent;
          break;
        }
        eachParent = eachParent.getParent();
      }

      if (frame == null) {
        frame = tryToFindTheOnlyFrame();
      }
    }

    LOG.assertTrue(frame != null, "Project: " + project);

    return frame;
  }

  private IdeFrameImpl tryToFindTheOnlyFrame() {
    IdeFrameImpl candidate = null;
    final Frame[] all = Frame.getFrames();
    for (Frame each : all) {
      if (each instanceof IdeFrameImpl) {
        if (candidate == null) {
          candidate = (IdeFrameImpl)each;
        } else {
          candidate = null;
          break;
        }
      }
    }
    return candidate;
  }

  public final IdeFrameImpl getFrame(final Project project) {
    // no assert! otherwise WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    return myProject2Frame.get(project);
  }

  public IdeFrame getIdeFrame(final Project project) {
    if (project != null) {
      return getFrame(project);
    } else {
      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      final Component parent = UIUtil.findUltimateParent(window);
      if (parent instanceof IdeFrame) return (IdeFrame)parent;

      final Frame[] frames = Frame.getFrames();
      for (Frame each : frames) {
        if (each instanceof IdeFrame) {
          return (IdeFrame)each;
        }
      }
    }

    return null;
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
      frame = new IdeFrameImpl((ApplicationInfoEx)ApplicationInfo.getInstance(), ActionManager.getInstance(), UISettings.getInstance(),
                               DataManager.getInstance(), KeymapManager.getInstance(), ApplicationManager.getApplication(), ArrayUtil.EMPTY_STRING_ARRAY);
      if (myFrameBounds != null) {
        frame.setBounds(myFrameBounds);
      }
      frame.setProject(project);
      myProject2Frame.put(project, frame);
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
      Disposer.dispose((StatusBarEx) frame.getStatusBar());
      frame.dispose();
    }
  }

  public final Window getMostRecentFocusedWindow() {
    return myWindowWatcher.getFocusedWindow();
  }

  public final Component getFocusedComponent(@NotNull final Window window) {
    return myWindowWatcher.getFocusedComponent(window);
  }

  public final Component getFocusedComponent(final Project project) {
    return myWindowWatcher.getFocusedComponent(project);
  }

  /**
   * Private part
   */
  public final CommandProcessor getCommandProcessor() {
    return myCommandProcessor;
  }

  public final String getExternalFileName() {
    return "window.manager";
  }

  public final void readExternal(final Element element) {
    final Element frameElement = element.getChild(FRAME_ELEMENT);
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

    final Element desktopElement = element.getChild(DesktopLayout.TAG);
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

  public final void writeExternal(final Element element) {
    // Save frame bounds
    final Element frameElement = new Element(FRAME_ELEMENT);
    element.addContent(frameElement);
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    final Project project;
    if (projects.length > 0) {
      project = projects[projects.length - 1];
    }
    else {
      project = null;
    }

    final IdeFrameImpl frame = getFrame(project);
    if (frame != null) {
      final Rectangle rectangle = frame.getBounds();
      frameElement.setAttribute(X_ATTR, Integer.toString(rectangle.x));
      frameElement.setAttribute(Y_ATTR, Integer.toString(rectangle.y));
      frameElement.setAttribute(WIDTH_ATTR, Integer.toString(rectangle.width));
      frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(rectangle.height));
      frameElement.setAttribute(EXTENDED_STATE_ATTR, Integer.toString(frame.getExtendedState()));

      // Save default layout
      final Element layoutElement = new Element(DesktopLayout.TAG);
      element.addContent(layoutElement);
      myLayout.writeExternal(layoutElement);
    }
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
   * focused window changes sporadically when user clickes on menu item or submenu. The problem
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
}
