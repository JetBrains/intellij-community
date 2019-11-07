// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManagerListener;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.ProjectFrameAllocatorKt;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.platform.WindowUtils;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

@State(
  name = "WindowManager",
  defaultStateAsResource = true,
  storages = {
    @Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "window.manager.xml", roamingType = RoamingType.DISABLED, deprecated = true),
  }
)
public final class WindowManagerImpl extends WindowManagerEx implements PersistentStateComponentWithModificationTracker<Element> {
  private static final Logger LOG = Logger.getInstance(WindowManagerImpl.class);

  @NonNls private static final String FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow";
  @NonNls private static final String FRAME_ELEMENT = "frame";

  private Boolean myAlphaModeSupported;

  private final EventDispatcher<WindowManagerListener> myEventDispatcher = EventDispatcher.create(WindowManagerListener.class);

  private final CommandProcessor myCommandProcessor = new CommandProcessor();
  private final WindowWatcher myWindowWatcher = new WindowWatcher();
  /**
   * That is the default layout.
   */
  private final DesktopLayout myLayout = new DesktopLayout();

  // null keys must be supported
  // null key - root frame
  private final Map<Project, ProjectFrameHelper> myProjectToFrame = new HashMap<>();

  private final Map<Project, Set<JDialog>> myDialogsToDispose = new THashMap<>();

  final FrameInfoHelper defaultFrameInfoHelper = new FrameInfoHelper();

  private final WindowAdapter myActivationListener = new WindowAdapter() {
    @Override
    public void windowActivated(WindowEvent e) {
      Window activeWindow = e.getWindow();
      ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(activeWindow);
      Project project = frameHelper == null ? null : frameHelper.getProject();
      if (project != null) {
        proceedDialogDisposalQueue(project);
      }
    }
  };

  private final ComponentAdapter myFrameStateListener = new ComponentAdapter() {
    @Override
    public void componentMoved(@NotNull ComponentEvent e) {
      update(e);
    }

    @Override
    public void componentResized(ComponentEvent e) {
      update(e);
    }

    private void update(@NotNull ComponentEvent e) {
      IdeFrameImpl frame = (IdeFrameImpl)e.getComponent();
      if (UIUtil.isClientPropertyTrue(frame.getRootPane(), ScreenUtil.DISPOSE_TEMPORARY)
          || UIUtil.isClientPropertyTrue(frame.getRootPane(), IdeFrameImpl.TOGGLING_FULL_SCREEN_IN_PROGRESS)) {
        return;
      }

      int extendedState = frame.getExtendedState();
      Rectangle bounds = frame.getBounds();
      JRootPane rootPane = frame.getRootPane();
      if (extendedState == Frame.NORMAL && rootPane != null) {
        rootPane.putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds);
      }

      ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(frame);
      if (frameHelper == null) {
        return;
      }

      Project project = frameHelper.getProject();
      if (project == null) {
        // Component moved during project loading - update myDefaultFrameInfo directly.
        // Cannot mark as dirty and compute later, because to convert user space info to device space,
        // we need graphicsConfiguration, but we can get graphicsConfiguration only from frame,
        // but later, when getStateModificationCount or getState is called, may be no frame at all.
        defaultFrameInfoHelper.updateFrameInfo(frameHelper);
      }
      else {
        ProjectFrameBounds projectFrameBounds = ProjectFrameBounds.getInstance(project);
        projectFrameBounds.markDirty(FrameInfoHelper.isMaximized(extendedState) ? null : bounds);
      }
    }
  };

  public WindowManagerImpl() {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      Disposer.register(application, this::disposeRootFrame);
    }

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, myWindowWatcher);

    if (UIUtil.hasLeakingAppleListeners()) {
      UIUtil.addAwtListener(event -> {
        if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
          if (((ContainerEvent)event).getChild() instanceof JViewport) {
            UIUtil.removeLeakingAppleListeners();
          }
        }
      }, AWTEvent.CONTAINER_EVENT_MASK, application);
    }
  }

  @Override
  @NotNull
  public ProjectFrameHelper[] getAllProjectFrames() {
    return myProjectToFrame.values().toArray(new ProjectFrameHelper[0]);
  }

  @NotNull
  @Override
  public List<ProjectFrameHelper> getProjectFrameHelpers() {
    return new ArrayList<>(myProjectToFrame.values());
  }

  @Override
  public JFrame findVisibleFrame() {
    ProjectFrameHelper firstFrameHelper = ContainerUtil.getFirstItem(myProjectToFrame.values());
    return firstFrameHelper == null ? (JFrame)WelcomeFrame.getInstance() : firstFrameHelper.getFrame();
  }

  @Override
  @Nullable
  public IdeFrameEx findFirstVisibleFrameHelper() {
    return ContainerUtil.getFirstItem(myProjectToFrame.values());
  }

  @Override
  public void addListener(@NotNull WindowManagerListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void removeListener(@NotNull WindowManagerListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  @Override
  public final Rectangle getScreenBounds() {
    return ScreenUtil.getAllScreensRectangle();
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

  @Override
  public final boolean isInsideScreenBounds(final int x, final int y, final int width) {
    return ScreenUtil.getAllScreensShape().contains(x, y, width, 1);
  }

  @Override
  public final boolean isAlphaModeSupported() {
    if (myAlphaModeSupported == null) {
      myAlphaModeSupported = calcAlphaModelSupported();
    }
    return myAlphaModeSupported.booleanValue();
  }

  private static boolean calcAlphaModelSupported() {
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
      return true;
    }
    try {
      return WindowUtils.isWindowAlphaSupported();
    }
    catch (Throwable e) {
      return false;
    }
  }

  @Override
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
      else if (isTranslucencySupported()) {
        window.setOpacity(1.0f - ratio);
      }
      else {
        WindowUtils.setWindowAlpha(window, 1.0f - ratio);
      }
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
  }

  @Override
  public void setWindowMask(final Window window, @Nullable final Shape mask) {
    try {
      if (isPerPixelTransparencySupported()) {
        window.setShape(mask);
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

  @Override
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

  @Override
  public final boolean isAlphaModeEnabled(final Window window) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    return isAlphaModeSupported();
  }

  @Override
  public final void setAlphaModeEnabled(final Window window, final boolean state) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
  }

  @Override
  public void hideDialog(JDialog dialog, Project project) {
    if (project == null) {
      dialog.dispose();
    }
    else {
      JFrame frame = getFrame(project);
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
          size.height += newSize.height - oldSize.height;
          popup.setSize(size);
          popup.moveToFitScreen();
        }
      }
    }
  }

  @Override
  public final void doNotSuggestAsParent(final Window window) {
    myWindowWatcher.doNotSuggestAsParent(window);
  }

  @Override
  public final void dispatchComponentEvent(final ComponentEvent e) {
    myWindowWatcher.dispatchComponentEvent(e);
  }

  @Override
  @Nullable
  public final Window suggestParentWindow(@Nullable Project project) {
    return myWindowWatcher.suggestParentWindow(project, this);
  }

  @Override
  public StatusBar getStatusBar(@NotNull Project project) {
    ProjectFrameHelper frame = getFrameHelper(project);
    return frame == null ? null : frame.getStatusBar();
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

  @Override
  public IdeFrame findFrameFor(@Nullable Project project) {
    IdeFrame frame;
    if (project != null) {
      frame = project.isDefault() ? WelcomeFrame.getInstance() : getFrameHelper(project);
      if (frame == null) {
        frame = getFrameHelper(null);
      }
    }
    else {
      frame = ProjectFrameHelper.getFrameHelper(getMostRecentFocusedWindow());
      if (frame == null) {
        frame = tryToFindTheOnlyFrame();
      }
    }
    return frame;
  }

  @Nullable
  private static IdeFrame tryToFindTheOnlyFrame() {
    IdeFrameImpl candidate = null;
    for (Frame each : Frame.getFrames()) {
      if (each instanceof IdeFrameImpl) {
        if (candidate == null) {
          candidate = (IdeFrameImpl)each;
        }
        else {
          candidate = null;
          break;
        }
      }
    }
    return candidate == null ? null : ProjectFrameHelper.getFrameHelper(candidate);
  }

  @Override
  public final IdeFrameImpl getFrame(@Nullable Project project) {
    // no assert! otherwise WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    ProjectFrameHelper helper = getFrameHelper(project);
    return helper == null ? null : helper.getFrame();
  }

  @Override
  @Nullable
  @ApiStatus.Internal
  public ProjectFrameHelper getFrameHelper(@Nullable Project project) {
    return myProjectToFrame.get(project);
  }

  @Nullable
  @Override
  public ProjectFrameHelper findFrameHelper(@Nullable Project project) {
    if (project == null) {
      IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
      if (frame == null) {
        return null;
      }

      project = frame.getProject();
      if (project == null) {
        return null;
      }
    }
    return getFrameHelper(project);
  }

  @Nullable
  @ApiStatus.Internal
  public IdeRootPane getProjectFrameRootPane(@Nullable Project project) {
    ProjectFrameHelper helper = myProjectToFrame.get(project);
    return helper == null ? null : helper.getRootPane();
  }

  @Override
  @Nullable
  public IdeFrame getIdeFrame(@Nullable Project project) {
    if (project != null) {
      return getFrameHelper(project);
    }

    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    IdeFrame result = window != null ? getIdeFrame(UIUtil.findUltimateParent(window)) : null;
    if (result != null) {
      return result;
    }

    for (Frame each : Frame.getFrames()) {
      result = getIdeFrame(each);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Nullable
  private static IdeFrame getIdeFrame(@NotNull Component component) {
    if (component instanceof IdeFrameImpl) {
      return ProjectFrameHelper.getFrameHelper((IdeFrameImpl)component);
    }
    else if (component instanceof IdeFrame) {
      return (IdeFrame)component;
    }
    else {
      return null;
    }
  }

  @Nullable
  @ApiStatus.Internal
  public ProjectFrameHelper removeAndGetRootFrame() {
    return myProjectToFrame.remove(null);
  }

  public void assignFrame(@NotNull ProjectFrameHelper frame, @NotNull Project project) {
    LOG.assertTrue(!myProjectToFrame.containsKey(project));

    frame.setProject(project);
    myProjectToFrame.put(project, frame);

    frame.getFrame().addWindowListener(myActivationListener);
    frame.getFrame().addComponentListener(myFrameStateListener);
  }

  @Override
  @NotNull
  public final ProjectFrameHelper allocateFrame(@NotNull Project project) {
    ProjectFrameHelper frame = getFrameHelper(project);
    if (frame != null) {
      myEventDispatcher.getMulticaster().frameCreated(frame);
      return frame;
    }

    frame = removeAndGetRootFrame();
    boolean isNewFrame = frame == null;
    FrameInfo frameInfo = null;
    if (isNewFrame) {
      frame = new ProjectFrameHelper(ProjectFrameAllocatorKt.createNewProjectFrame(), null);
      frame.init();

      frameInfo = ProjectFrameBounds.getInstance(project).getFrameInfoInDeviceSpace();
      if (frameInfo == null || frameInfo.getBounds() == null) {
        IdeFrame lastFocusedFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
        Project lastFocusedProject = lastFocusedFrame == null ? null : lastFocusedFrame.getProject();
        if (lastFocusedProject != null) {
          frameInfo = ProjectFrameBounds.getInstance(lastFocusedProject).getActualFrameInfoInDeviceSpace(frame, this);
        }

        if (frameInfo == null || frameInfo.getBounds() == null) {
          frameInfo = defaultFrameInfoHelper.getInfo();
        }
      }

      if (frameInfo != null && frameInfo.getBounds() != null) {
        // update default frame info - newly opened project frame should be the same as last opened
        if (frameInfo != defaultFrameInfoHelper.getInfo()) {
          defaultFrameInfoHelper.copyFrom(frameInfo);
        }

        Rectangle bounds = frameInfo.getBounds();
        if (bounds != null) {
          frame.getFrame().setBounds(FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(bounds));
        }
      }
    }

    frame.setProject(project);
    myProjectToFrame.put(project, frame);

    if (isNewFrame) {
      if (frameInfo != null) {
        frame.getFrame().setExtendedState(frameInfo.getExtendedState());
      }
      frame.getFrame().setVisible(true);

      if (FrameInfoHelper.isFullScreenSupportedInCurrentOs() &&
          ((frameInfo != null && frameInfo.getFullScreen()))) {
        frame.toggleFullScreen(true);
      }
    }

    frame.getFrame().addWindowListener(myActivationListener);
    if (isNewFrame) {
      frame.getFrame().addComponentListener(myFrameStateListener);
      IdeMenuBar.installAppMenuIfNeeded(frame.getFrame());
    }

    myEventDispatcher.getMulticaster().frameCreated(frame);

    return frame;
  }

  private void proceedDialogDisposalQueue(@NotNull Project project) {
    Set<JDialog> dialogs = myDialogsToDispose.remove(project);
    if (dialogs != null) {
      dialogs.forEach(Window::dispose);
    }
  }

  private void queueForDisposal(@NotNull JDialog dialog, @NotNull Project project) {
    myDialogsToDispose.computeIfAbsent(project, k -> new THashSet<>()).add(dialog);
  }

  @Override
  public final void releaseFrame(@NotNull ProjectFrameHelper frameHelper) {
    myEventDispatcher.getMulticaster().beforeFrameReleased(frameHelper);

    JFrame frame = frameHelper.getFrame();
    Project project = frameHelper.getProject();
    LOG.assertTrue(project != null);

    frameHelper.getFrame().removeWindowListener(myActivationListener);
    proceedDialogDisposalQueue(project);

    frameHelper.setProject(null);
    frame.setTitle(null);
    frameHelper.setFileTitle(null, null);

    myProjectToFrame.remove(project);
    if (myProjectToFrame.isEmpty()) {
      myProjectToFrame.put(null, frameHelper);
    }
    else {
      StatusBar statusBar = frameHelper.getStatusBar();
      if (statusBar != null) {
        Disposer.dispose(statusBar);
      }
      frame.dispose();
    }
  }

  public final void disposeRootFrame() {
    if (myProjectToFrame.size() == 1) {
      final ProjectFrameHelper rootFrame = removeAndGetRootFrame();
      if (rootFrame != null) {
        // disposing last frame if quitting
        rootFrame.getFrame().dispose();
      }
    }
  }

  @Override
  public final Window getMostRecentFocusedWindow() {
    return myWindowWatcher.getFocusedWindow();
  }

  @Override
  public final Component getFocusedComponent(@NotNull final Window window) {
    return myWindowWatcher.getFocusedComponent(window);
  }

  @Override
  @Nullable
  public final Component getFocusedComponent(@Nullable final Project project) {
    return myWindowWatcher.getFocusedComponent(project);
  }

  /**
   * Private part
   */
  @Override
  @NotNull
  public final CommandProcessor getCommandProcessor() {
    return myCommandProcessor;
  }

  @Override
  public void loadState(@NotNull Element state) {
    final Element frameElement = state.getChild(FRAME_ELEMENT);
    if (frameElement != null) {
      FrameInfo info = new FrameInfo();
      XmlSerializer.deserializeInto(frameElement, info);

      // backward compatibility - old name of extendedState attribute
      if (info.getExtendedState() == Frame.NORMAL) {
        String extendedState = frameElement.getAttributeValue("extended-state");
        if (extendedState != null) {
          info.setExtendedState(StringUtil.parseInt(extendedState, Frame.NORMAL));
        }
      }
      if ((info.getExtendedState() & Frame.ICONIFIED) > 0) {
        info.setExtendedState(Frame.NORMAL);
      }

      defaultFrameInfoHelper.copyFrom(info);
    }

    final Element desktopElement = state.getChild(DesktopLayout.TAG);
    if (desktopElement != null) {
      myLayout.readExternal(desktopElement);
    }
  }

  @Override
  public long getStateModificationCount() {
    return defaultFrameInfoHelper.getModificationCount() + myLayout.getStateModificationCount();
  }

  @Nullable
  @ApiStatus.Internal
  public FrameInfo getDefaultFrameInfo() {
    return defaultFrameInfoHelper.getInfo();
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    FrameInfo frameInfo = defaultFrameInfoHelper.getInfo();
    Element frameElement = frameInfo == null ? null : XmlSerializer.serialize(frameInfo);
    if (frameElement != null) {
      state.addContent(frameElement);
    }

    // save default layout
    Element layoutElement = myLayout.writeExternal(DesktopLayout.TAG);
    if (layoutElement != null) {
      state.addContent(layoutElement);
    }
    return state;
  }

  @Override
  public final DesktopLayout getLayout() {
    return myLayout;
  }

  @Override
  public final void setLayout(final DesktopLayout layout) {
    myLayout.copyFrom(layout);
  }

  public WindowWatcher getWindowWatcher() {
    return myWindowWatcher;
  }

  @Override
  public boolean isFullScreenSupportedInCurrentOS() {
    return FrameInfoHelper.isFullScreenSupportedInCurrentOs();
  }

  static boolean isFloatingMenuBarSupported() {
    return !SystemInfo.isMac && FrameInfoHelper.isFullScreenSupportedInCurrentOs();
  }

  static boolean isTranslucencySupported() {
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
  }

  static boolean isPerPixelTransparencySupported() {
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT);
  }
}
