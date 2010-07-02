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

import com.intellij.Patches;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.commands.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.switcher.QuickAccessSettings;
import com.intellij.ui.switcher.SwitchManager;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ToolWindowManagerImpl extends ToolWindowManagerEx implements ProjectComponent, JDOMExternalizable, KeyEventDispatcher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.ToolWindowManagerImpl");

  private final Project myProject;
  private final WindowManagerEx myWindowManager;
  private final EventListenerList myListenerList;
  private final DesktopLayout myLayout;
  private final HashMap<String, InternalDecorator> myId2InternalDecorator;
  private final HashMap<String, FloatingDecorator> myId2FloatingDecorator;
  private final HashMap<String, StripeButton> myId2StripeButton;
  private final HashMap<String, FocusWatcher> myId2FocusWatcher;
  private final Set<String> myDumbAwareIds = Collections.synchronizedSet(CollectionFactory.<String>newTroveSet());

  private final EditorComponentFocusWatcher myEditorComponentFocusWatcher;
  private final MyToolWindowPropertyChangeListener myToolWindowPropertyChangeListener;
  private final InternalDecoratorListener myInternalDecoratorListener;

  private boolean myEditorComponentActive;
  private final ActiveStack myActiveStack;
  private final SideStack mySideStack;

  private ToolWindowsPane myToolWindowsPane;
  private IdeFrameImpl myFrame;
  private DesktopLayout myLayoutToRestoreLater = null;
  @NonNls private static final String EDITOR_ELEMENT = "editor";
  @NonNls private static final String ACTIVE_ATTR_VALUE = "active";
  @NonNls private static final String FRAME_ELEMENT = "frame";
  @NonNls private static final String X_ATTR = "x";
  @NonNls private static final String Y_ATTR = "y";
  @NonNls private static final String WIDTH_ATTR = "width";
  @NonNls private static final String HEIGHT_ATTR = "height";
  @NonNls private static final String EXTENDED_STATE_ATTR = "extended-state";


  private final Set<String> myRestoredToolWindowIds = new HashSet<String>();
  private final FileEditorManager myFileEditorManager;

  private final Map<String, Balloon> myWindow2Balloon = new HashMap<String, Balloon>();

  private KeyState myCurrentState = KeyState.waiting;
  private final Alarm myWaiterForSecondPress = new Alarm();
  private final Runnable mySecondPressRunnable = new Runnable() {
    public void run() {
      if (myCurrentState != KeyState.hold) {
        resetHoldState();
      }
    }
  };

  private enum KeyState {
    waiting, pressed, released, hold
  }

  /**
   * invoked by reflection
   */
  public ToolWindowManagerImpl(final Project project, WindowManagerEx windowManagerEx, final FileEditorManager fem) {
    myProject = project;
    myWindowManager = windowManagerEx;
    myFileEditorManager = fem;
    myListenerList = new EventListenerList();

    myLayout = new DesktopLayout();
    myLayout.copyFrom(windowManagerEx.getLayout());

    myId2InternalDecorator = new HashMap<String, InternalDecorator>();
    myId2FloatingDecorator = new HashMap<String, FloatingDecorator>();
    myId2StripeButton = new HashMap<String, StripeButton>();
    myId2FocusWatcher = new HashMap<String, FocusWatcher>();

    myEditorComponentFocusWatcher = new EditorComponentFocusWatcher();
    myToolWindowPropertyChangeListener = new MyToolWindowPropertyChangeListener();
    myInternalDecoratorListener = new MyInternalDecoratorListener();

    myEditorComponentActive = false;
    myActiveStack = new ActiveStack();
    mySideStack = new SideStack();

    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener(){
      public void fileOpened(FileEditorManager source, VirtualFile file) {
      }

      public void fileClosed(FileEditorManager source, VirtualFile file) {
        getFocusManagerImpl().doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            if (!hasOpenEditorFiles()) {
              focusToolWinowByDefault(null);
            }
          }
        });
      }

      public void selectionChanged(FileEditorManagerEvent event) {
      }
    });
  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getKeyCode() != KeyEvent.VK_CONTROL && e.getKeyCode() != KeyEvent.VK_ALT && e.getKeyCode() != KeyEvent.VK_SHIFT && e.getKeyCode() != KeyEvent.VK_META) {
      if (e.getModifiers() == 0) {
        resetHoldState();
      }
      return false;
    }

    if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED) return false;

    Component parent = UIUtil.findUltimateParent(e.getComponent());
    if (parent instanceof IdeFrame) {
      if (((IdeFrame)parent).getProject() != myProject) {
        resetHoldState();
        return false;
      }
    }

    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] baseShortcut = keymap.getShortcuts("ActivateProjectToolWindow");
    int baseModifiers = 0;
    for (Shortcut each : baseShortcut) {
      if (each instanceof KeyboardShortcut) {
        KeyStroke keyStroke = ((KeyboardShortcut)each).getFirstKeyStroke();
        baseModifiers = keyStroke.getModifiers();
        if (baseModifiers > 0) {
          break;
        }
      }
    }

    if (baseModifiers == 0) {
      resetHoldState();
      return false;
    }

    Set<Integer> vks = QuickAccessSettings.getModifiersVKs(baseModifiers);
    if (vks.contains(e.getKeyCode())) {
      boolean pressed = e.getID() == KeyEvent.KEY_PRESSED;
      int modifiers = e.getModifiers();

      int mouseMask = MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK;
      if ((e.getModifiersEx() & mouseMask) == 0) {
        if (SwitchManager.areAllModifiersPressed(modifiers, vks) || !pressed) {
          processState(pressed);
        } else {
          resetHoldState();
        }
      }
    }
    

    return false;
  }

  private void resetHoldState() {
    myCurrentState = KeyState.waiting;
    processHoldState();
  }

  private void resetState() {
    myCurrentState = KeyState.waiting;
  }

  private void processState(boolean pressed) {
    if (pressed) {
      if (myCurrentState == KeyState.waiting) {
        myCurrentState = KeyState.pressed;
      } else if (myCurrentState == KeyState.released) {
        myCurrentState = KeyState.hold;
        processHoldState();
      } 
    } else {
      if (myCurrentState == KeyState.pressed) {
        myCurrentState = KeyState.released;
        restartWaitingForSecondPressAlarm();
      } else if (myCurrentState == KeyState.hold) {
        resetHoldState();
      } else {
        resetHoldState();
      }
    }
  }

  private void processHoldState() {
    myToolWindowsPane.setStripesOverlayed(myCurrentState == KeyState.hold);
  }

  private void restartWaitingForSecondPressAlarm() {
    myWaiterForSecondPress.cancelAllRequests();
    myWaiterForSecondPress.addRequest(mySecondPressRunnable, Registry.intValue("actionSystem.keyGestureDblClickTime"));
  }

  private boolean hasOpenEditorFiles() {
    return myFileEditorManager.getOpenFiles().length > 0;
  }

  FocusManagerImpl getFocusManagerImpl() {
    return FocusManagerImpl.getInstance();
  }

  public Project getProject() {
    return myProject;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    final MyUIManagerPropertyChangeListener myUIManagerPropertyChangeListener = new MyUIManagerPropertyChangeListener();
    final MyLafManagerListener myLafManagerListener = new MyLafManagerListener();

    UIManager.addPropertyChangeListener(myUIManagerPropertyChangeListener);
    LafManager.getInstance().addLafManagerListener(myLafManagerListener);

    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        UIManager.removePropertyChangeListener(myUIManagerPropertyChangeListener);
        LafManager.getInstance().removeLafManagerListener(myLafManagerListener);
      }
    });
    myFrame = myWindowManager.allocateFrame(myProject);
    LOG.assertTrue(myFrame != null);

    final ArrayList<FinalizableCommand> commandsList = new ArrayList<FinalizableCommand>();

    myToolWindowsPane = new ToolWindowsPane(myFrame, this);
    ((IdeRootPane)myFrame.getRootPane()).setToolWindowsPane(myToolWindowsPane);
    appendUpdateToolWindowsPaneCmd(commandsList);

    myFrame.setTitle(FrameTitleBuilder.getInstance().getProjectTitle(myProject));

    final JComponent editorComponent = FileEditorManagerEx.getInstanceEx(myProject).getComponent();
    myEditorComponentFocusWatcher.install(editorComponent);

    appendSetEditorComponentCmd(editorComponent, commandsList);
    if (myEditorComponentActive) {
      activateEditorComponentImpl(commandsList, true);
    }
    execute(commandsList);

    final DumbService.DumbModeListener dumbModeListener = new DumbService.DumbModeListener() {
      public void enteredDumbMode() {
        for (final String id : getToolWindowIds()) {
          if (!myDumbAwareIds.contains(id)) {
            if (isToolWindowVisible(id)) {
              hideToolWindow(id, true);
            }
            getStripeButton(id).setEnabled(false);
          }
        }
      }

      public void exitDumbMode() {
        for (final String id : getToolWindowIds()) {
          getStripeButton(id).setEnabled(true);
        }
      }
    };
    myProject.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, dumbModeListener);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        registerToolWindowsFromBeans();
        if (DumbService.getInstance(myProject).isDumb()) {
          dumbModeListener.enteredDumbMode();
        }
      }
    });

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  private void registerToolWindowsFromBeans() {
    ToolWindowEP[] beans = Extensions.getExtensions(ToolWindowEP.EP_NAME);
    for (final ToolWindowEP bean : beans) {
      final Condition condition = bean.getCondition();
      if (condition != null && !condition.value(myProject)) {
        continue;
      }
      ToolWindowAnchor toolWindowAnchor;
      try {
        toolWindowAnchor = ToolWindowAnchor.fromText(bean.anchor);
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }
      JLabel label = new JLabel("Initializing...", JLabel.CENTER);
      label.setOpaque(true);
      final Color treeBg = UIManager.getColor("Tree.background");
      label.setBackground(new Color(treeBg.getRed(), treeBg.getGreen(), treeBg.getBlue(), 180));
      final Color treeFg = UIManager.getColor("Tree.foreground");
      label.setForeground(new Color(treeFg.getRed(), treeFg.getGreen(), treeFg.getBlue(), 180));
      final ToolWindowFactory factory = bean.getToolWindowFactory();
      final ToolWindowImpl toolWindow = (ToolWindowImpl)registerToolWindow(bean.id, label, toolWindowAnchor, myProject, factory instanceof DumbAware);
      toolWindow.setContentFactory(factory);
      if (bean.icon != null) {
        Icon icon = IconLoader.findIcon(bean.icon, factory.getClass());
        if (icon == null) {
          try {
            icon = IconLoader.getIcon(bean.icon);
          } catch (Exception ignored) {}
        }
        toolWindow.setIcon(icon);
      }

      if (!getInfo(bean.id).isSplit() && bean.secondary && !myRestoredToolWindowIds.contains(bean.id)) {
        toolWindow.setSplitMode(bean.secondary, null);
      }

      final ActionCallback activation = toolWindow.setActivation(new ActionCallback());

      UiNotifyConnector.doWhenFirstShown(label, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
            public void run() {
              toolWindow.ensureContentInitialized();
              activation.setDone();
            }
          });
        }
      });
    }
  }

  public void projectClosed() {
    final ArrayList<FinalizableCommand> commandsList = new ArrayList<FinalizableCommand>();
    final String[] ids = getToolWindowIds();

    // Remove ToolWindowsPane

    ((IdeRootPane)myFrame.getRootPane()).setToolWindowsPane(null);
    myWindowManager.releaseFrame(myFrame);
    appendUpdateToolWindowsPaneCmd(commandsList);

    // Hide all tool windows

    for (final String id : ids) {
      deactivateToolWindowImpl(id, true, commandsList);
    }

    // Remove editor component

    final JComponent editorComponent = FileEditorManagerEx.getInstanceEx(myProject).getComponent();
    myEditorComponentFocusWatcher.deinstall(editorComponent);
    appendSetEditorComponentCmd(null, commandsList);
    execute(commandsList);

    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
  }

  public void addToolWindowManagerListener(@NotNull final ToolWindowManagerListener l) {
    myListenerList.add(ToolWindowManagerListener.class, l);
  }

  public void removeToolWindowManagerListener(@NotNull final ToolWindowManagerListener l) {
    myListenerList.remove(ToolWindowManagerListener.class, l);
  }

  /**
   * This is helper method. It delegated its fuctionality to the WindowManager.
   * Before delegating it fires state changed.
   */
  private void execute(final ArrayList<FinalizableCommand> commandList) {
    for (FinalizableCommand each : commandList) {
      if (each.willChangeState()) {
        fireStateChanged();
        break;
      }
    }

    for (FinalizableCommand each : commandList) {
      each.beforeExecute(this);
    }
    myWindowManager.getCommandProcessor().execute(commandList, myProject.getDisposed());
  }

  public void activateEditorComponent() {
    activateEditorComponent(true);
  }

  private void activateEditorComponent(boolean forced) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: activateEditorComponent()");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    activateEditorComponentImpl(commandList, forced);
    execute(commandList);
  }

  private void activateEditorComponentImpl(final ArrayList<FinalizableCommand> commandList, final boolean forced) {
    final String active = getActiveToolWindowId();
    // Now we have to request focus into most recent focused editor
    appendRequestFocusInEditorComponentCmd(commandList, forced).doWhenDone(new Runnable() {
      public void run() {
        final ArrayList<FinalizableCommand> postExecute = new ArrayList<FinalizableCommand>();

        if (LOG.isDebugEnabled()) {
          LOG.debug("editor activated");
        }
        deactivateWindows(postExecute, null);
        myActiveStack.clear();
        myEditorComponentActive = true;

        execute(postExecute);
      }
    }).doWhenRejected(new Runnable() {
      public void run() {
        if (forced) {
          getFocusManagerImpl().requestFocus(new FocusCommand() {
            public ActionCallback run() {
              final ArrayList<FinalizableCommand> cmds = new ArrayList<FinalizableCommand>();

              final WindowInfoImpl toReactivate = getInfo(active);
              final boolean reactivateLastActive = toReactivate != null && !isToHide(toReactivate);
              deactivateWindows(cmds, reactivateLastActive ? active : null);
              execute(cmds);

              if (reactivateLastActive) {
                activateToolWindow(active, false, true);
              }
              else {
                if (active != null) {
                  myActiveStack.remove(active, false);
                }

                if (!myActiveStack.isEmpty()) {
                  activateToolWindow(myActiveStack.peek(), false, true);
                }
              }
              return new ActionCallback.Done();
            }
          }, false);
        }
      }
    });
  }

  private void deactivateWindows(final ArrayList<FinalizableCommand> postExecute, String idToIgnore) {
    final WindowInfoImpl[] infos = myLayout.getInfos();
    for (final WindowInfoImpl info : infos) {
      final boolean shouldHide = isToHide(info);
      if (idToIgnore != null && idToIgnore.equals(info.getId())) {
        continue;
      }
      deactivateToolWindowImpl(info.getId(), shouldHide, postExecute);
    }
  }

  private boolean isToHide(final WindowInfoImpl info) {
    return (info.isAutoHide() || info.isSliding()) && !(info.isFloating() && hasModalChild(info));
  }

  /**
   * Helper method. It makes window visible, activates it and request focus into the tool window.
   * But it doesn't deactivate other tool windows. Use <code>prepareForActivation</code> method to
   * deactivates other tool windows.
   *
   * @param dirtyMode if <code>true</code> then all UI operations are performed in "dirty" mode.
   *                  It means that UI isn't validated and repainted just after each add/remove operation.
   * @see ToolWindowManagerImpl#prepareForActivation
   */
  private void showAndActivate(final String id,
                               final boolean dirtyMode,
                               final ArrayList<FinalizableCommand> commandsList,
                               boolean autoFocusContents) {
    if (!getToolWindow(id).isAvailable()) {
      return;
    }
    // show activated
    final WindowInfoImpl info = getInfo(id);
    boolean toApplyInfo = false;
    if (!info.isActive()) {
      info.setActive(true);
      toApplyInfo = true;
    }
    showToolWindowImpl(id, dirtyMode, commandsList);

    // activate
    if (toApplyInfo) {
      appendApplyWindowInfoCmd(info, commandsList);
      myActiveStack.push(id);
      myEditorComponentActive = false;
    }

    if (autoFocusContents) {
      appendRequestFocusInToolWindowCmd(id, commandsList, true);
    }
  }

  void activateToolWindow(final String id, boolean forced, boolean autoFocusContents) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: activateToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    if (DumbService.getInstance(myProject).isDumb() && !myDumbAwareIds.contains(id)) {
      return;
    }

    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    activateToolWindowImpl(id, commandList, forced, autoFocusContents);
    execute(commandList);
  }

  private void activateToolWindowImpl(final String id,
                                      final ArrayList<FinalizableCommand> commandList,
                                      boolean forced,
                                      boolean autoFocusContents) {
    if (!getFocusManagerImpl().isUnforcedRequestAllowed() && !forced) return;

    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: activateToolWindowImpl(" + id + ")");
    }
    if (!getToolWindow(id).isAvailable()) {
      // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      final InternalDecorator decorator = getInternalDecorator(id);
      if (!decorator.hasFocus() && autoFocusContents) {
        appendRequestFocusInToolWindowCmd(id, commandList, forced);
      }
      return;
    }
    prepareForActivation(id, commandList);
    showAndActivate(id, false, commandList, autoFocusContents);
  }

  /**
   * Checkes whether the specified <code>id</code> defines installed tool
   * window. If it's not then throws <code>IllegalStateException</code>.
   *
   * @throws IllegalStateException if tool window isn't installed.
   */
  private void checkId(final String id) {
    if (!myLayout.isToolWindowRegistered(id)) {
      throw new IllegalStateException("window with id=\"" + id + "\" isn't registered");
    }
  }

  /**
   * Helper method. It deactivates (and hides) window with specified <code>id</code>.
   *
   * @param id         <code>id</code> of the tool window to be deactivated.
   * @param shouldHide if <code>true</code> then also hides specified tool window.
   */
  private void deactivateToolWindowImpl(final String id, final boolean shouldHide, final List<FinalizableCommand> commandsList) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: deactivateToolWindowImpl(" + id + "," + shouldHide + ")");
    }
    final WindowInfoImpl info = getInfo(id);
    if (shouldHide && info.isVisible()) {
      info.setVisible(false);
      if (info.isFloating()) {
        appendRemoveFloatingDecoratorCmd(info, commandsList);
      }
      else { // docked and sliding windows
        appendRemoveDecoratorCmd(id, false, commandsList);
      }
    }
    info.setActive(false);
    appendApplyWindowInfoCmd(info, commandsList);
  }

  public String[] getToolWindowIds() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final WindowInfoImpl[] infos = myLayout.getInfos();
    final String[] ids = ArrayUtil.newStringArray(infos.length);
    for (int i = 0; i < infos.length; i++) {
      ids[i] = infos[i].getId();
    }
    return ids;
  }

  public String getActiveToolWindowId() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLayout.getActiveId();
  }

  public String getLastActiveToolWindowId() {
    return getLastActiveToolWindowId(null);
  }

  public String getLastActiveToolWindowId(Condition<JComponent> condition) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String lastActiveToolWindowId = null;
    for (int i = 0; i < myActiveStack.getPersistentSize(); i++) {
      final String id = myActiveStack.peekPersistent(i);
      final ToolWindow toolWindow = getToolWindow(id);
      LOG.assertTrue(toolWindow != null);
      if (toolWindow.isAvailable()) {
        if (condition == null || condition.value(toolWindow.getComponent())) {
          lastActiveToolWindowId = id;
          break;
        }
      }
    }
    return lastActiveToolWindowId;
  }

  /**
   * @return floating decorator for the tool window with specified <code>ID</code>.
   */
  private FloatingDecorator getFloatingDecorator(final String id) {
    return myId2FloatingDecorator.get(id);
  }

  /**
   * @return internal decorator for the tool window with specified <code>ID</code>.
   */
  private InternalDecorator getInternalDecorator(final String id) {
    return myId2InternalDecorator.get(id);
  }

  /**
   * @return tool button for the window with specified <code>ID</code>.
   */
  private StripeButton getStripeButton(final String id) {
    return myId2StripeButton.get(id);
  }

  /**
   * @return info for the tool window with specified <code>ID</code>.
   */
  private WindowInfoImpl getInfo(final String id) {
    return myLayout.getInfo(id, true);
  }

  public List<String> getIdsOn(@NotNull final ToolWindowAnchor anchor) {
    return myLayout.getVisibleIdsOn(anchor, this);
  }

  public ToolWindow getToolWindow(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myLayout.isToolWindowRegistered(id)) {
      return null;
    }
    InternalDecorator decorator = getInternalDecorator(id);
    return decorator != null ? decorator.getToolWindow() : null;
  }

  void showToolWindow(final String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: showToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    showToolWindowImpl(id, false, commandList);
    execute(commandList);
  }

  public void hideToolWindow(@NotNull final String id, final boolean hideSide) {
    hideToolWindow(id, hideSide, true);
  }

  public void hideToolWindow(final String id, final boolean hideSide, final boolean moveFocus) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    final WindowInfoImpl info = getInfo(id);
    if (!info.isVisible()) return;
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    final boolean wasActive = info.isActive();

    // hide and deactivate

    deactivateToolWindowImpl(id, true, commandList);

    if (hideSide || info.isFloating()) {
      final List<String> ids = myLayout.getVisibleIdsOn(info.getAnchor(), this);
      for (String each : ids) {
        myActiveStack.remove(each, true);
      }


      while (!mySideStack.isEmpty(info.getAnchor())) {
        mySideStack.pop(info.getAnchor());
      }

      final String[] all = getToolWindowIds();
      for (String eachId : all) {
        final WindowInfoImpl eachInfo = getInfo(eachId);
        if (eachInfo.isVisible() && eachInfo.getAnchor() == info.getAnchor()) {
          deactivateToolWindowImpl(eachId, true, commandList);
        }
      }

      activateEditorComponentImpl(commandList, true);
    }
    else {

      // first of all we have to find tool window that was located at the same side and
      // was hidden.

      WindowInfoImpl info2 = null;
      while (!mySideStack.isEmpty(info.getAnchor())) {
        final WindowInfoImpl storedInfo = mySideStack.pop(info.getAnchor());
        final WindowInfoImpl currentInfo = getInfo(storedInfo.getId());
        LOG.assertTrue(currentInfo != null);
        // SideStack contains copies of real WindowInfos. It means that
        // these stored infos can be invalid. The following loop removes invalid WindowInfos.
        if (storedInfo.getAnchor() == currentInfo.getAnchor() &&
            storedInfo.getType() == currentInfo.getType() &&
            storedInfo.isAutoHide() == currentInfo.isAutoHide()) {
          info2 = storedInfo;
          break;
        }
      }
      if (info2 != null) {
        showToolWindowImpl(info2.getId(), false, commandList);
      }

      // If we hide currently active tool window then we should activate the previous
      // one which is located in the tool window stack.
      // Activate another tool window if no active tool window exists and
      // window stack is enabled.

      myActiveStack.remove(id, false); // hidden window should be at the top of stack

      if (wasActive && moveFocus) {
        if (myActiveStack.isEmpty()) {
          if (hasOpenEditorFiles()) {
            activateEditorComponentImpl(commandList, false);
          } else {
            focusToolWinowByDefault(id);
          }
        }
        else {
          final String toBeActivatedId = myActiveStack.pop();
          if (toBeActivatedId != null) {
            activateToolWindowImpl(toBeActivatedId, commandList, false, true);
          } else {
            focusToolWinowByDefault(id);
          }
        }
      }
    }

    execute(commandList);
  }

  /**
   * @param dirtyMode if <code>true</code> then all UI operations are performed in dirty mode.
   */
  private void showToolWindowImpl(final String id, final boolean dirtyMode, final List<FinalizableCommand> commandsList) {
    final WindowInfoImpl toBeShownInfo = getInfo(id);
    if (toBeShownInfo.isVisible() || !getToolWindow(id).isAvailable()) {
      return;
    }

    if (DumbService.getInstance(myProject).isDumb() && !myDumbAwareIds.contains(id)) {
      return;
    }

    toBeShownInfo.setVisible(true);
    final InternalDecorator decorator = getInternalDecorator(id);

    if (toBeShownInfo.isFloating()) {
      commandsList.add(new AddFloatingDecoratorCmd(decorator, toBeShownInfo));
    }
    else { // docked and sliding windows

      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.

      final WindowInfoImpl[] infos = myLayout.getInfos();
      for (final WindowInfoImpl info : infos) {
        if (id.equals(info.getId())) {
          continue;
        }
        if (info.isVisible() &&
            info.getType() == toBeShownInfo.getType() &&
            info.getAnchor() == toBeShownInfo.getAnchor() &&
            info.isSplit() == toBeShownInfo.isSplit()) {
          // hide and deactivate tool window
          info.setVisible(false);
          appendRemoveDecoratorCmd(info.getId(), false, commandsList);
          if (info.isActive()) {
            info.setActive(false);
          }
          appendApplyWindowInfoCmd(info, commandsList);
          // store WindowInfo into the SideStack
          if (info.isDocked() && !info.isAutoHide()) {
            mySideStack.push(info);
          }
        }
      }
      appendAddDecoratorCmd(decorator, toBeShownInfo, dirtyMode, commandsList);

      // Remove tool window from the SideStack.

      mySideStack.remove(id);
    }

    appendApplyWindowInfoCmd(toBeShownInfo, commandsList);
  }

  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull final JComponent component,
                                       @NotNull final ToolWindowAnchor anchor) {
    return registerToolWindow(id, component, anchor, false);
  }

  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       Disposable parentDisposable) {
    return registerToolWindow(id, component, anchor, parentDisposable, false);
  }

  public ToolWindow registerToolWindow(@NotNull final String id,
                                       @NotNull JComponent component,
                                       @NotNull ToolWindowAnchor anchor,
                                       Disposable parentDisposable,
                                       boolean canWorkInDumbMode) {
    return registerDisposable(id, parentDisposable, registerToolWindow(id, component, anchor, canWorkInDumbMode));
  }

  private ToolWindow registerToolWindow(@NotNull final String id,
                                        @NotNull final JComponent component,
                                        @NotNull final ToolWindowAnchor anchor,
                                        boolean canWorkInDumbMode) {
    return registerToolWindow(id, component, anchor, false, false, canWorkInDumbMode);
  }

  public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
    return registerToolWindow(id, null, anchor, false, canCloseContent, false);
  }

  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       final boolean sideTool) {
    return registerToolWindow(id, null, anchor, sideTool, canCloseContent, false);
  }


  public ToolWindow registerToolWindow(@NotNull final String id,
                                       final boolean canCloseContent,
                                       @NotNull final ToolWindowAnchor anchor,
                                       final Disposable parentDisposable,
                                       final boolean canWorkInDumbMode) {
    return registerDisposable(id, parentDisposable, registerToolWindow(id, null, anchor, false, canCloseContent, canWorkInDumbMode));
  }

  private ToolWindow registerToolWindow(@NotNull final String id,
                                        @Nullable final JComponent component,
                                        @NotNull final ToolWindowAnchor anchor,
                                        boolean sideTool,
                                        boolean canCloseContent,
                                        final boolean canWorkInDumbMode) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: installToolWindow(" + id + "," + component + "," + anchor + "\")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myLayout.isToolWindowRegistered(id)) {
      throw new IllegalArgumentException("window with id=\"" + id + "\" is already registered");
    }

    final WindowInfoImpl info = myLayout.register(id, anchor, sideTool);
    final boolean wasActive = info.isActive();
    final boolean wasVisible = info.isVisible();
    info.setActive(false);
    info.setVisible(false);

    // Create decorator

    final ToolWindowImpl toolWindow = new ToolWindowImpl(this, id, canCloseContent, component);
    final InternalDecorator decorator = new InternalDecorator(myProject, info.copy(), toolWindow);
    myId2InternalDecorator.put(id, decorator);
    decorator.addInternalDecoratorListener(myInternalDecoratorListener);
    toolWindow.addPropertyChangeListener(myToolWindowPropertyChangeListener);
    myId2FocusWatcher.put(id, new ToolWindowFocusWatcher(toolWindow));

    // Create and show tool button

    final StripeButton button = new StripeButton(decorator, myToolWindowsPane);
    myId2StripeButton.put(id, button);
    final ArrayList<FinalizableCommand> commandsList = new ArrayList<FinalizableCommand>();
    appendAddButtonCmd(button, info, commandsList);

    if (canWorkInDumbMode) {
      myDumbAwareIds.add(id);
    } else if (DumbService.getInstance(getProject()).isDumb()) {
      button.setEnabled(false);
    }

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the autohide
    // mode. But if tool window was active but its mode doen't allow to activate it again
    // (for example, tool window is in autohide mode) then we just activate editor component.

    if (!info.isAutoHide() && (info.isDocked() || info.isFloating())) {
      if (wasActive) {
        activateToolWindowImpl(info.getId(), commandsList, true, true);
      }
      else if (wasVisible) {
        showToolWindowImpl(info.getId(), false, commandsList);
      }
    }
    else if (wasActive) { // tool window was active but it cannot be activate again
      activateEditorComponentImpl(commandsList, true);
    }

    execute(commandsList);
    fireToolWindowRegistered(id);
    return toolWindow;
  }

  private ToolWindow registerDisposable(final String id, final Disposable parentDisposable, final ToolWindow window) {
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterToolWindow(id);
      }
    });
    return window;
  }

  public void unregisterToolWindow(@NotNull final String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: unregisterToolWindow(" + id + ")");
    }
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myLayout.isToolWindowRegistered(id)) {
      return;
    }

    final WindowInfoImpl info = getInfo(id);
    final ToolWindowEx toolWindow = (ToolWindowEx)getToolWindow(id);
    // Save recent appearance of tool window
    myLayout.unregister(id);
    // Remove decorator and tool button from the screen
    final ArrayList<FinalizableCommand> commandsList = new ArrayList<FinalizableCommand>();
    if (info.isVisible()) {
      info.setVisible(false);
      if (info.isFloating()) {
        appendRemoveFloatingDecoratorCmd(info, commandsList);
      }
      else { // floating and sliding windows
        appendRemoveDecoratorCmd(id, false, commandsList);
      }
    }
    appendRemoveButtonCmd(id, commandsList);
    appendApplyWindowInfoCmd(info, commandsList);
    execute(commandsList);
    // Remove all references on tool window and save its last properties
    toolWindow.removePropertyChangeListener(myToolWindowPropertyChangeListener);
    myActiveStack.remove(id, true);
    mySideStack.remove(id);
    // Destroy stripe button
    final StripeButton button = getStripeButton(id);
    button.dispose();
    myId2StripeButton.remove(id);
    //
    myId2FocusWatcher.remove(id);
    // Destroy decorator
    final InternalDecorator decorator = getInternalDecorator(id);
    decorator.dispose();
    decorator.removeInternalDecoratorListener(myInternalDecoratorListener);
    myId2InternalDecorator.remove(id);
  }

  public DesktopLayout getLayout() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myLayout;
  }

  public void setLayoutToRestoreLater(DesktopLayout layout) {
    myLayoutToRestoreLater = layout;
  }

  public DesktopLayout getLayoutToRestoreLater() {
    return myLayoutToRestoreLater;
  }

  public void setLayout(@NotNull final DesktopLayout layout) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    // hide tool window that are invisible in new layout
    final WindowInfoImpl[] currentInfos = myLayout.getInfos();
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.isVisible() && !info.isVisible()) {
        deactivateToolWindowImpl(currentInfo.getId(), true, commandList);
      }
    }
    // change anchor of tool windows
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.getAnchor() != info.getAnchor() || currentInfo.getOrder() != info.getOrder()) {
        setToolWindowAnchorImpl(currentInfo.getId(), info.getAnchor(), info.getOrder(), commandList);
      }
    }
    // change types of tool windows
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.getType() != info.getType()) {
        setToolWindowTypeImpl(currentInfo.getId(), info.getType(), commandList);
      }
    }
    // change auto-hide state
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
      if (info == null) {
        continue;
      }
      if (currentInfo.isAutoHide() != info.isAutoHide()) {
        setToolWindowAutoHideImpl(currentInfo.getId(), info.isAutoHide(), commandList);
      }
    }
    // restore visibility
    for (final WindowInfoImpl currentInfo : currentInfos) {
      final WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
      if (info == null) {
        continue;
      }
      if (info.isVisible()) {
        showToolWindowImpl(currentInfo.getId(), false, commandList);
      }
    }
    // if there is no any active tool window and editor is also inactive
    // then activate editor
    if (!myEditorComponentActive && getActiveToolWindowId() == null) {
      activateEditorComponentImpl(commandList, true);
    }
    execute(commandList);
  }

  public void invokeLater(final Runnable runnable) {
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    commandList.add(new InvokeLaterCmd(runnable, myWindowManager.getCommandProcessor()));
    execute(commandList);
  }

  public IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody) {
    notifyByBalloon(toolWindowId, type, htmlBody, null, null);
  }

  public void notifyByBalloon(@NotNull final String toolWindowId,
                              @NotNull final MessageType type,
                              @NotNull final String text,
                              @Nullable final Icon icon,
                              @Nullable HyperlinkListener listener) {
    checkId(toolWindowId);


    Balloon existing = myWindow2Balloon.get(toolWindowId);
    if (existing != null) {
      existing.hide();
    }

    final Stripe stripe = myToolWindowsPane.getStripeFor(toolWindowId);
    final ToolWindowImpl window = getInternalDecorator(toolWindowId).getToolWindow();
    if (!window.isAvailable()) {
      window.setPlaceholderMode(true);
      stripe.updateState();
      stripe.revalidate();
      stripe.repaint();
    }

    final ToolWindowAnchor anchor = getInfo(toolWindowId).getAnchor();
    final Ref<Balloon.Position> position = Ref.create(Balloon.Position.below);
    if (ToolWindowAnchor.TOP == anchor) {
      position.set(Balloon.Position.below);
    }
    else if (ToolWindowAnchor.BOTTOM == anchor) {
      position.set(Balloon.Position.above);
    }
    else if (ToolWindowAnchor.LEFT == anchor) {
      position.set(Balloon.Position.atRight);
    }
    else if (ToolWindowAnchor.RIGHT == anchor) {
      position.set(Balloon.Position.atLeft);
    }

    Icon actualIcon = icon != null ? icon : type.getDefaultIcon();

    final Balloon balloon =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(text.replace("\n", "<br>"), actualIcon, type.getPopupBackground(), listener).setHideOnClickOutside(true).setHideOnFrameResize(false)
        .createBalloon();
    myWindow2Balloon.put(toolWindowId, balloon);
    Disposer.register(balloon, new Disposable() {
      public void dispose() {
        window.setPlaceholderMode(false);
        stripe.updateState();
        stripe.revalidate();
        stripe.repaint();
        myWindow2Balloon.remove(toolWindowId);
      }
    });
    Disposer.register(getProject(), balloon);

    final StripeButton button = stripe.getButtonFor(toolWindowId);
    if (button == null) return;

    final Runnable show = new Runnable() {
      public void run() {
        if (button.isShowing()) {
          PositionTracker tracker = new PositionTracker<Balloon>(button) {
            @Override
            public RelativePoint recalculateLocation(Balloon object) {
              Stripe twStripe = myToolWindowsPane.getStripeFor(toolWindowId);
              StripeButton twButton = twStripe != null ? twStripe.getButtonFor(toolWindowId) : null;

              if (twButton == null) return null;

              if (getToolWindow(toolWindowId).getAnchor() != anchor) {
                object.hide();
                return null;
              }

              final Point point = new Point(twButton.getBounds().width / 2, twButton.getHeight() / 2 - 2);
              return new RelativePoint(twButton, point);
            }
          };
          balloon.show(tracker, position.get());
        }
        else {
          final Rectangle bounds = myToolWindowsPane.getBounds();
          final Point target = UIUtil.getCenterPoint(bounds, new Dimension(1, 1));
          if (ToolWindowAnchor.TOP == anchor) {
            target.y = 0;
          }
          else if (ToolWindowAnchor.BOTTOM == anchor) {
            target.y = bounds.height - 3;
          }
          else if (ToolWindowAnchor.LEFT == anchor) {
            target.x = 0;
          }
          else if (ToolWindowAnchor.RIGHT == anchor) {
            target.x = bounds.width;
          }

          balloon.show(new RelativePoint(myToolWindowsPane, target), position.get());
        }
      }
    };

    if (!button.isValid()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          show.run();
        }
      });
    }
    else {
      show.run();
    }

  }

  public boolean isEditorComponentActive() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myEditorComponentActive;
  }

  ToolWindowAnchor getToolWindowAnchor(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).getAnchor();
  }

  void setToolWindowAnchor(final String id, final ToolWindowAnchor anchor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    setToolWindowAnchor(id, anchor, -1);
  }

  void setToolWindowAnchor(final String id, final ToolWindowAnchor anchor, final int order) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    setToolWindowAnchorImpl(id, anchor, order, commandList);
    execute(commandList);
  }

  private void setToolWindowAnchorImpl(final String id,
                                       final ToolWindowAnchor anchor,
                                       final int order,
                                       final ArrayList<FinalizableCommand> commandsList) {
    checkId(id);
    final WindowInfoImpl info = getInfo(id);
    if (anchor == info.getAnchor() && order == info.getOrder()) {
      return;
    }
    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!info.isVisible() || anchor == info.getAnchor() || info.isFloating()) {
      appendRemoveButtonCmd(id, commandsList);
      myLayout.setAnchor(id, anchor, order);
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      final WindowInfoImpl[] infos = myLayout.getInfos();
      for (WindowInfoImpl info1 : infos) {
        appendApplyWindowInfoCmd(info1, commandsList);
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList);
    }
    else { // for docked and sliding windows we have to move buttons and window's decorators
      info.setVisible(false);
      appendRemoveDecoratorCmd(id, false, commandsList);
      appendRemoveButtonCmd(id, commandsList);
      myLayout.setAnchor(id, anchor, order);
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      final WindowInfoImpl[] infos = myLayout.getInfos();
      for (WindowInfoImpl info1 : infos) {
        appendApplyWindowInfoCmd(info1, commandsList);
      }
      appendAddButtonCmd(getStripeButton(id), info, commandsList);
      showToolWindowImpl(id, false, commandsList);
      if (info.isActive()) {
        appendRequestFocusInToolWindowCmd(id, commandsList, true);
      }
    }
  }

  boolean isSplitMode(String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).isSplit();
  }

  ToolWindowContentUiType getContentUiType(String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).getContentUiType();
  }

  void setSideTool(String id, boolean isSide) {
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    setSplitModeImpl(id, isSide, commandList);
    execute(commandList);
  }

  public void setContentUiType(String id, ToolWindowContentUiType type) {
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    checkId(id);
    WindowInfoImpl info = getInfo(id);
    info.setContentUiType(type);
    appendApplyWindowInfoCmd(info, commandList);
    execute(commandList);
  }

  void setSideToolAndAnchor(String id, ToolWindowAnchor anchor, int order, boolean isSide) {
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    setToolWindowAnchor(id, anchor, order);
    setSplitModeImpl(id, isSide, commandList);
    execute(commandList);
  }

  private void setSplitModeImpl(final String id, final boolean isSplit, final ArrayList<FinalizableCommand> commandList) {
    checkId(id);
    final WindowInfoImpl info = getInfo(id);
    if (isSplit == info.isSplit()) {
      return;
    }

    myLayout.setSplitMode(id, isSplit);

    boolean wasActive = info.isActive();
    if (wasActive) {
      deactivateToolWindowImpl(id, true, commandList);
    }
    final WindowInfoImpl[] infos = myLayout.getInfos();
    for (WindowInfoImpl info1 : infos) {
      appendApplyWindowInfoCmd(info1, commandList);
    }
    if (wasActive) {
      activateToolWindowImpl(id, commandList, true, true);
    }
    commandList.add(myToolWindowsPane.createUpdateButtonPositionCmd(id, myWindowManager.getCommandProcessor()));
  }

  ToolWindowType getToolWindowInternalType(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).getInternalType();
  }

  ToolWindowType getToolWindowType(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).getType();
  }

  private void fireToolWindowRegistered(final String id) {
    final ToolWindowManagerListener[] listeners = myListenerList.getListeners(ToolWindowManagerListener.class);
    for (ToolWindowManagerListener listener : listeners) {
      listener.toolWindowRegistered(id);
    }
  }

  private void fireStateChanged() {
    final ToolWindowManagerListener[] listeners = myListenerList.getListeners(ToolWindowManagerListener.class);
    for (ToolWindowManagerListener listener : listeners) {
      listener.stateChanged();
    }
  }

  boolean isToolWindowActive(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).isActive();
  }

  boolean isToolWindowAutoHide(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).isAutoHide();
  }

  public boolean isToolWindowFloating(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).isFloating();
  }

  boolean isToolWindowVisible(final String id) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkId(id);
    return getInfo(id).isVisible();
  }

  void setToolWindowAutoHide(final String id, final boolean autoHide) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    setToolWindowAutoHideImpl(id, autoHide, commandList);
    execute(commandList);
  }

  private void setToolWindowAutoHideImpl(final String id, final boolean autoHide, final ArrayList<FinalizableCommand> commandsList) {
    checkId(id);
    final WindowInfoImpl info = getInfo(id);
    if (info.isAutoHide() == autoHide) {
      return;
    }
    info.setAutoHide(autoHide);
    appendApplyWindowInfoCmd(info, commandsList);
    if (info.isVisible()) {
      prepareForActivation(id, commandsList);
      showAndActivate(id, false, commandsList, true);
    }
  }

  void setToolWindowType(final String id, final ToolWindowType type) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final ArrayList<FinalizableCommand> commandList = new ArrayList<FinalizableCommand>();
    setToolWindowTypeImpl(id, type, commandList);
    execute(commandList);
  }

  private void setToolWindowTypeImpl(final String id, final ToolWindowType type, final ArrayList<FinalizableCommand> commandsList) {
    checkId(id);
    final WindowInfoImpl info = getInfo(id);
    if (info.getType() == type) {
      return;
    }
    if (info.isVisible()) {
      final boolean dirtyMode = info.isDocked() || info.isSliding();
      info.setVisible(false);
      if (info.isFloating()) {
        appendRemoveFloatingDecoratorCmd(info, commandsList);
      }
      else { // docked and sliding windows
        appendRemoveDecoratorCmd(id, dirtyMode, commandsList);
      }
      info.setType(type);
      appendApplyWindowInfoCmd(info, commandsList);
      prepareForActivation(id, commandsList);
      showAndActivate(id, dirtyMode, commandsList, true);
      appendUpdateToolWindowsPaneCmd(commandsList);
    }
    else {
      info.setType(type);
      appendApplyWindowInfoCmd(info, commandsList);
    }
  }

  private void appendApplyWindowInfoCmd(final WindowInfoImpl info, final List<FinalizableCommand> commandsList) {
    final StripeButton button = getStripeButton(info.getId());
    final InternalDecorator decorator = getInternalDecorator(info.getId());
    commandsList.add(new ApplyWindowInfoCmd(info, button, decorator, myWindowManager.getCommandProcessor()));
  }

  /**
   * @see com.intellij.openapi.wm.impl.ToolWindowsPane#createAddDecoratorCmd
   */
  private void appendAddDecoratorCmd(final InternalDecorator decorator,
                                     final WindowInfoImpl info,
                                     final boolean dirtyMode,
                                     final List<FinalizableCommand> commandsList) {
    final CommandProcessor commandProcessor = myWindowManager.getCommandProcessor();
    final FinalizableCommand command = myToolWindowsPane.createAddDecoratorCmd(decorator, info, dirtyMode, commandProcessor);
    commandsList.add(command);
  }

  /**
   * @see com.intellij.openapi.wm.impl.ToolWindowsPane#createRemoveDecoratorCmd
   */
  private void appendRemoveDecoratorCmd(final String id, final boolean dirtyMode, final List<FinalizableCommand> commandsList) {
    final FinalizableCommand command = myToolWindowsPane.createRemoveDecoratorCmd(id, dirtyMode, myWindowManager.getCommandProcessor());
    commandsList.add(command);
  }

  private void appendRemoveFloatingDecoratorCmd(final WindowInfoImpl info, final List<FinalizableCommand> commandsList) {
    final RemoveFloatingDecoratorCmd command = new RemoveFloatingDecoratorCmd(info);
    commandsList.add(command);
  }

  /**
   * @see com.intellij.openapi.wm.impl.ToolWindowsPane#createAddButtonCmd
   */
  private void appendAddButtonCmd(final StripeButton button, final WindowInfoImpl info, final List<FinalizableCommand> commandsList) {
    final Comparator<StripeButton> comparator = myLayout.comparator(info.getAnchor());
    final CommandProcessor commandProcessor = myWindowManager.getCommandProcessor();
    final FinalizableCommand command = myToolWindowsPane.createAddButtonCmd(button, info, comparator, commandProcessor);
    commandsList.add(command);
  }

  /**
   * @see com.intellij.openapi.wm.impl.ToolWindowsPane#createAddButtonCmd
   */
  private void appendRemoveButtonCmd(final String id, final List<FinalizableCommand> commandsList) {
    final FinalizableCommand command = myToolWindowsPane.createRemoveButtonCmd(id, myWindowManager.getCommandProcessor());
    commandsList.add(command);
  }

  private ActionCallback appendRequestFocusInEditorComponentCmd(final ArrayList<FinalizableCommand> commandList, boolean forced) {
    if (myProject.isDisposed()) return new ActionCallback.Done();
    final CommandProcessor commandProcessor = myWindowManager.getCommandProcessor();
    final RequestFocusInEditorComponentCmd command =
      new RequestFocusInEditorComponentCmd(FileEditorManagerEx.getInstanceEx(myProject), getFocusManager(), commandProcessor, forced);
    commandList.add(command);
    return command.getDoneCallback();
  }

  private void appendRequestFocusInToolWindowCmd(final String id, final ArrayList<FinalizableCommand> commandList, boolean forced) {
    final ToolWindowImpl toolWindow = (ToolWindowImpl)getToolWindow(id);
    final FocusWatcher focusWatcher = myId2FocusWatcher.get(id);
    commandList.add(new RequestFocusInToolWindowCmd(getFocusManager(), toolWindow, focusWatcher, myWindowManager.getCommandProcessor(), forced));
  }

  /**
   * @see com.intellij.openapi.wm.impl.ToolWindowsPane#createSetEditorComponentCmd
   */
  private void appendSetEditorComponentCmd(final JComponent component, final List<FinalizableCommand> commandsList) {
    final CommandProcessor commandProcessor = myWindowManager.getCommandProcessor();
    final FinalizableCommand command = myToolWindowsPane.createSetEditorComponentCmd(component, commandProcessor);
    commandsList.add(command);
  }

  private void appendUpdateToolWindowsPaneCmd(final List<FinalizableCommand> commandsList) {
    final JRootPane rootPane = myFrame.getRootPane();
    final FinalizableCommand command = new UpdateRootPaneCmd(rootPane, myWindowManager.getCommandProcessor());
    commandsList.add(command);
  }

  /**
   * @return <code>true</code> if tool window with the specified <code>id</code>
   *         is floating and has modal showing child dialog. Such windows should not be closed
   *         when auto-hide windows are gone.
   */
  private boolean hasModalChild(final WindowInfoImpl info) {
    if (!info.isVisible() || !info.isFloating()) {
      return false;
    }
    final FloatingDecorator decorator = getFloatingDecorator(info.getId());
    LOG.assertTrue(decorator != null);
    return isModalOrHasModalChild(decorator);
  }

  private static boolean isModalOrHasModalChild(final Window window) {
    if (window instanceof Dialog) {
      final Dialog dialog = (Dialog)window;
      if (dialog.isModal() && dialog.isShowing()) {
        return true;
      }
      final Window[] ownedWindows = dialog.getOwnedWindows();
      for (int i = ownedWindows.length - 1; i >= 0; i--) {
        if (isModalOrHasModalChild(ownedWindows[i])) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Helper method. It deactivates all tool windows excepting the tool window
   * which should be activated.
   */
  private void prepareForActivation(final String id, final List<FinalizableCommand> commandList) {
    final WindowInfoImpl toBeActivatedInfo = getInfo(id);
    final WindowInfoImpl[] infos = myLayout.getInfos();
    for (final WindowInfoImpl info : infos) {
      if (id.equals(info.getId())) {
        continue;
      }
      if (toBeActivatedInfo.isDocked() || toBeActivatedInfo.isSliding()) {
        deactivateToolWindowImpl(info.getId(), info.isAutoHide() || info.isSliding(), commandList);
      }
      else { // floating window is being activated
        deactivateToolWindowImpl(info.getId(), info.isAutoHide() && info.isFloating() && !hasModalChild(info), commandList);
      }
    }
  }

  public void clearSideStack() {
    mySideStack.clear();
  }

  public void readExternal(final Element element) {
    for (final Object o : element.getChildren()) {
      final Element e = (Element)o;
      if (EDITOR_ELEMENT.equals(e.getName())) {
        myEditorComponentActive = Boolean.valueOf(e.getAttributeValue(ACTIVE_ATTR_VALUE)).booleanValue();
      }
      else if (DesktopLayout.TAG.equals(e.getName())) { // read layout of tool windows
        myLayout.readExternal(e);
        final WindowInfoImpl[] windowInfos = myLayout.getAllInfos();
        for (WindowInfoImpl windowInfo : windowInfos) {
          myRestoredToolWindowIds.add(windowInfo.getId());
        }
      }
    }
  }

  public void writeExternal(final Element element) {
    if (myFrame == null) {
      // do nothing if the project was not opened
      return;
    }
    final String[] ids = getToolWindowIds();

    // Update size of all open floating windows. See SCR #18439
    for (final String id : ids) {
      final WindowInfoImpl info = getInfo(id);
      if (info.isVisible()) {
        final InternalDecorator decorator = getInternalDecorator(id);
        LOG.assertTrue(decorator != null);
        decorator.fireResized();
      }
    }

    // Save frame's bounds
    final Rectangle frameBounds = myFrame.getBounds();
    final Element frameElement = new Element(FRAME_ELEMENT);
    element.addContent(frameElement);
    frameElement.setAttribute(X_ATTR, Integer.toString(frameBounds.x));
    frameElement.setAttribute(Y_ATTR, Integer.toString(frameBounds.y));
    frameElement.setAttribute(WIDTH_ATTR, Integer.toString(frameBounds.width));
    frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(frameBounds.height));
    frameElement.setAttribute(EXTENDED_STATE_ATTR, Integer.toString(myFrame.getExtendedState()));
    // Save whether editor is active or not
    final Element editorElement = new Element(EDITOR_ELEMENT);
    editorElement.setAttribute(ACTIVE_ATTR_VALUE, myEditorComponentActive ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
    element.addContent(editorElement);
    // Save layout of tool windows
    final Element layoutElement = new Element(DesktopLayout.TAG);
    element.addContent(layoutElement);
    myLayout.writeExternal(layoutElement);
  }

  public void setDefaultState(@NotNull final ToolWindowImpl toolWindow,
                              @Nullable final ToolWindowAnchor anchor,
                              @Nullable final ToolWindowType type,
                              @Nullable final Rectangle floatingBounds) {

    final WindowInfoImpl info = getInfo(toolWindow.getId());
    if (info.wasRead()) return;

    if (floatingBounds != null) {
      info.setFloatingBounds(floatingBounds);
    }

    if (anchor != null) {
      toolWindow.setAnchor(anchor, null);
    }

    if (type != null) {
      toolWindow.setType(type, null);
    }

  }

  public void setDefaultContentUiType(ToolWindowImpl toolWindow, ToolWindowContentUiType type) {
    final WindowInfoImpl info = getInfo(toolWindow.getId());
    if (info.wasRead()) return;
    toolWindow.setContentUiType(type, null);
  }



  public void stretchWidth(ToolWindowImpl toolWindow, int value) {
    myToolWindowsPane.stretchWidth(toolWindow, value);
  }

  public void stretchHeight(ToolWindowImpl toolWindow, int value) {
    myToolWindowsPane.stretchHeight(toolWindow, value);
  }


  /**
   * This command creates and shows <code>FloatingDecorator</code>.
   */
  private final class AddFloatingDecoratorCmd extends FinalizableCommand {
    private final FloatingDecorator myFloatingDecorator;

    /**
     * Creates floating decorator for specified floating decorator.
     */
    private AddFloatingDecoratorCmd(final InternalDecorator decorator, final WindowInfoImpl info) {
      super(myWindowManager.getCommandProcessor());
      myFloatingDecorator = new FloatingDecorator(myFrame, info.copy(), decorator);
      myId2FloatingDecorator.put(info.getId(), myFloatingDecorator);
      final Rectangle bounds = info.getFloatingBounds();
      if (bounds != null &&
          bounds.width > 0 &&
          bounds.height > 0 &&
          myWindowManager.isInsideScreenBounds(bounds.x, bounds.y, bounds.width)) {
        myFloatingDecorator.setBounds(bounds);
      }
      else { // place new frame at the center of main frame if there are no floating bounds
        Dimension size = decorator.getSize();
        if (size.width == 0 || size.height == 0) {
          size = decorator.getPreferredSize();
        }
        myFloatingDecorator.setSize(size);
        myFloatingDecorator.setLocationRelativeTo(myFrame);
      }
    }

    public void run() {
      try {
        myFloatingDecorator.show();
      }
      finally {
        finish();
      }
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified <code>ID</code>.
   */
  private final class RemoveFloatingDecoratorCmd extends FinalizableCommand {
    private final FloatingDecorator myFloatingDecorator;

    private RemoveFloatingDecoratorCmd(final WindowInfoImpl info) {
      super(myWindowManager.getCommandProcessor());
      myFloatingDecorator = getFloatingDecorator(info.getId());
      myId2FloatingDecorator.remove(info.getId());
      info.setFloatingBounds(myFloatingDecorator.getBounds());
    }

    public void run() {
      try {
        if (Patches.SPECIAL_WINPUT_METHOD_PROCESSING) {
          myFloatingDecorator.remove(myFloatingDecorator.getRootPane());
        }
        myFloatingDecorator.dispose();
      }
      finally {
        finish();
      }
    }

    @Nullable
    public Condition getExpireCondition() {
      return Condition.FALSE;
    }
  }

  private final class EditorComponentFocusWatcher extends FocusWatcher {
    protected void focusedComponentChanged(final Component component, final AWTEvent cause) {
      if (myWindowManager.getCommandProcessor().getCommandCount() > 0 || component == null) {
        return;
      }

      // Sometimes focus gained comes when editor is active. For example it can happen when
      // user switches between menus or closes some dialog. In that case we just ignore this event,
      // i.e. don't initiate deactivation of tool windows and requesting focus in editor.
      if (myEditorComponentActive) {
        return;
      }


      final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      final Component owner = mgr.getFocusOwner();

      IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          if (mgr.getFocusOwner() == owner) {
            activateEditorComponent(false);
          }
        }
      });
    }
  }


  /**
   * Notifies window manager about focus traversal in tool window
   */
  private final class ToolWindowFocusWatcher extends FocusWatcher {
    private final String myId;


    private ToolWindowFocusWatcher(final ToolWindowImpl toolWindow) {
      myId = toolWindow.getId();
      install(toolWindow.getComponent());
    }

    protected boolean isFocusedComponentChangeValid(final Component comp, final AWTEvent cause) {
      return myWindowManager.getCommandProcessor().getCommandCount() == 0 && comp != null;
    }

    protected void focusedComponentChanged(final Component component, final AWTEvent cause) {
      if (myWindowManager.getCommandProcessor().getCommandCount() > 0 || component == null) {
        return;
      }
      final WindowInfoImpl info = getInfo(myId);
      getFocusManagerImpl().myFocusedComponentAlaram.cancelAllRequests();

      if (!info.isActive()) {
        getFocusManagerImpl().myFocusedComponentAlaram.addRequest(new EdtRunnable() {
          public void runEdt() {
            if (!myLayout.isToolWindowRegistered(myId)) return;
            activateToolWindow(myId, false, false);
          }
        }, 100);
      }
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window
   * state.
   */
  private final class MyToolWindowPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
      final ToolWindowImpl toolWindow = (ToolWindowImpl)e.getSource();
      if (ToolWindowEx.PROP_AVAILABLE.equals(e.getPropertyName())) {
        final WindowInfoImpl info = getInfo(toolWindow.getId());
        if (!toolWindow.isAvailable() && info.isVisible()) {
          hideToolWindow(toolWindow.getId(), false);
        }
      }
    }
  }

  /**
   * Translates events from InternalDecorator into ToolWindowManager method invocations.
   */
  private final class MyInternalDecoratorListener implements InternalDecoratorListener {
    public void anchorChanged(final InternalDecorator source, final ToolWindowAnchor anchor) {
      setToolWindowAnchor(source.getToolWindow().getId(), anchor);
    }

    public void autoHideChanged(final InternalDecorator source, final boolean autoHide) {
      setToolWindowAutoHide(source.getToolWindow().getId(), autoHide);
    }

    public void hidden(final InternalDecorator source) {
      hideToolWindow(source.getToolWindow().getId(), false);
    }

    public void hiddenSide(final InternalDecorator source) {
      hideToolWindow(source.getToolWindow().getId(), true);
    }

    public void contentUiTypeChanges(InternalDecorator source, ToolWindowContentUiType type) {
      setContentUiType(source.getToolWindow().getId(), type);
    }

    /**
     * Handles event from decorator and modify weight/floating bounds of the
     * tool window depending on decoration type.
     */
    public void resized(final InternalDecorator source) {
      final WindowInfoImpl info = getInfo(source.getToolWindow().getId());
      if (info.isFloating()) {
        final Window owner = SwingUtilities.getWindowAncestor(source);
        if (owner != null) {
          info.setFloatingBounds(owner.getBounds());
        }
      }
      else { // docked and sliding windows
        if (ToolWindowAnchor.TOP == info.getAnchor() || ToolWindowAnchor.BOTTOM == info.getAnchor()) {
          info.setWeight((float)source.getHeight() / (float)myToolWindowsPane.getMyLayeredPane().getHeight());
          float newSideWeight = (float)source.getWidth() / (float)myToolWindowsPane.getMyLayeredPane().getWidth();
          if (newSideWeight < 1.0f) {
            info.setSideWeight(newSideWeight);
          }
        }
        else {
          info.setWeight((float)source.getWidth() / (float)myToolWindowsPane.getMyLayeredPane().getWidth());
          float newSideWeight = (float)source.getHeight() / (float)myToolWindowsPane.getMyLayeredPane().getHeight();
          if (newSideWeight < 1.0f) {
            info.setSideWeight(newSideWeight);
          }
        }
      }
    }

    public void activated(final InternalDecorator source) {
      activateToolWindow(source.getToolWindow().getId(), true, true);
    }

    public void typeChanged(final InternalDecorator source, final ToolWindowType type) {
      setToolWindowType(source.getToolWindow().getId(), type);
    }

    public void sideStatusChanged(final InternalDecorator source, final boolean isSideTool) {
      setSideTool(source.getToolWindow().getId(), isSideTool);
    }
  }

  private void updateComponentTreeUI() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final WindowInfoImpl[] infos = myLayout.getInfos();
    for (final WindowInfoImpl info : infos) {
      if (info.isVisible()) { // skip visible tool windows (optimization)
        continue;
      }
      SwingUtilities.updateComponentTreeUI(getInternalDecorator(info.getId()));
    }
  }

  private final class MyUIManagerPropertyChangeListener implements PropertyChangeListener {
    public void propertyChange(final PropertyChangeEvent e) {
      updateComponentTreeUI();
    }
  }

  private final class MyLafManagerListener implements LafManagerListener {
    public void lookAndFeelChanged(final LafManager source) {
      updateComponentTreeUI();
    }
  }


  @NotNull
  public String getComponentName() {
    return "ToolWindowManager";
  }

  public ToolWindowsPane getToolWindowsPane() {
    return myToolWindowsPane;
  }

  public ActionCallback requestDefaultFocus(final boolean forced) {
    return getFocusManagerImpl().requestFocus(new FocusCommand() {
      public ActionCallback run() {
        return processDefaultFocusRequest(forced);
      }
    }, forced);
  }

  private void focusToolWinowByDefault(String idToIngore) {
    String toFocus = null;

    for (String each : myActiveStack.getStack()) {
      if (idToIngore != null && idToIngore.equalsIgnoreCase(each)) continue;

      if (getInfo(each).isVisible()) {
        toFocus = each;
        break;
      }
    }

    if (toFocus == null) {
      for (String each : myActiveStack.getPersistentStack()) {
        if (idToIngore != null && idToIngore.equalsIgnoreCase(each)) continue;

        if (getInfo(each).isVisible()) {
          toFocus = each;
          break;
        }
      }
    }

    if (toFocus != null) {
      activateToolWindow(toFocus, false, true);
    }
  }

  private ActionCallback processDefaultFocusRequest(boolean forced) {
    if (ModalityState.NON_MODAL.equals(ModalityState.current())) {
      final String activeId = getActiveToolWindowId();
      if (myEditorComponentActive || activeId == null || getToolWindow(activeId) == null) {
        activateEditorComponent(forced);
      } else {
        activateToolWindow(activeId, forced, false);
      }
    }
    return new ActionCallback.Done();
  }





  private boolean isProjectComponent(Component c) {
    final Component frame = UIUtil.findUltimateParent(c);
    if (frame instanceof IdeFrame) {
      return frame == myWindowManager.getFrame(myProject);
    }
    else {
      return false;
    }
  }

  /**
   * Delegate method for compatibility with older versions of IDEA 
   */
  @NotNull
  public ActionCallback requestFocus(@NotNull Component c, boolean forced) {
    return IdeFocusManager.getInstance(myProject).requestFocus(c, forced);
  }

  @NotNull
  public ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced) {
    return IdeFocusManager.getInstance(myProject).requestFocus(command, forced);
  }

  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return IdeFocusManager.getInstance(myProject).getFocusTargetFor(comp);
  }

  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(runnable);
  }

  public Component getFocusedDescendantFor(Component comp) {
    return IdeFocusManager.getInstance(myProject).getFocusedDescendantFor(comp);
  }

  public boolean dispatch(KeyEvent e) {
    return IdeFocusManager.getInstance(myProject).dispatch(e);
  }

  public void suspendKeyProcessingUntil(@NotNull ActionCallback done) {
    IdeFocusManager.getInstance(myProject).suspendKeyProcessingUntil(done);
  }

  public boolean isFocusBeingTransferred() {
    return IdeFocusManager.getInstance(myProject).isFocusBeingTransferred();
  }

  public Expirable getTimestamp(boolean trackOnlyForcedCommands) {
    return IdeFocusManager.getInstance(myProject).getTimestamp(trackOnlyForcedCommands);
  }
}
