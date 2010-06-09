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
package com.intellij.openapi.keymap.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.keyGestures.KeyboardGestureProcessor;
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.im.InputContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is automaton with finite number of state.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IdeKeyEventDispatcher implements Disposable {
  @NonNls
  private static final String GET_CACHED_STROKE_METHOD_NAME = "getCachedStroke";

  private KeyStroke myFirstKeyStroke;
  /**
   * When we "dispatch" key event via keymap, i.e. when registered action has been executed
   * instead of event dispatching, then we have to consume all following KEY_RELEASED and
   * KEY_TYPED event because they are not valid.
   */
  private boolean myPressedWasProcessed;
  private KeyState myState = KeyState.STATE_INIT;

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private boolean myDisposed = false;
  private boolean myLeftCtrlPressed = false;
  private boolean myRightAltPressed = false;

  private final KeyboardGestureProcessor myKeyGestureProcessor = new KeyboardGestureProcessor(this);

  private final KeyProcessorContext myContext = new KeyProcessorContext();
  private final IdeEventQueue myQueue;

  private final Alarm mySecondStrokeTimeout = new Alarm();
  private final Runnable mySecondStrokeTimeoutRunnable = new Runnable() {
    public void run() {
      if (myState == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE) {
        resetState();
        if (myContext != null) {
          Project project = PlatformDataKeys.PROJECT.getData(myContext.getDataContext());
          if (project != null) {
            StatusBar.Info.set(null, project);
          }
        }
      }
    }
  };

  public IdeKeyEventDispatcher(IdeEventQueue queue){
    myQueue = queue;
    Application parent = ApplicationManager.getApplication();  // Application is null on early start when e.g. license dialog is shown
    if (parent != null) Disposer.register(parent, this);
  }

  public boolean isWaitingForSecondKeyStroke(){
    return getState() == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE || isPressedWasProcessed();
  }

  /**
   * @return <code>true</code> if and only if the passed event is already dispatched by the
   * <code>IdeKeyEventDispatcher</code> and there is no need for any other processing of the event.
   */
  public boolean dispatchKeyEvent(final KeyEvent e){
    if (myDisposed) return false;

    if(e.isConsumed()){
      return false;
    }

    // http://www.jetbrains.net/jira/browse/IDEADEV-12372
    if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        myLeftCtrlPressed = e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT;
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED) {
        myLeftCtrlPressed = false;
      }
    }
    else if (e.getKeyCode() == KeyEvent.VK_ALT) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        myRightAltPressed = e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT;
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED) {
        myRightAltPressed = false;
      }
    }

    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();

    // shortcuts should not work in shortcut setup fields
    if (focusOwner instanceof ShortcutTextField) {
      return false;
    }

    MenuSelectionManager menuSelectionManager=MenuSelectionManager.defaultManager();
    MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
    if(selectedPath.length>0){
      if (!(selectedPath[0] instanceof ComboPopup)) {
        // The following couple of lines of code is a PATCH!!!
        // It is needed to ignore ENTER KEY_TYPED events which sometimes can reach editor when an action
        // is invoked from main menu via Enter key.
        setState(KeyState.STATE_PROCESSED);
        setPressedWasProcessed(true);
        return false;
      }
    }

    // Keymap shortcuts (i.e. not local shortcuts) should work only in:
    // - main frame
    // - floating focusedWindow
    // - when there's an editor in contexts
    Window focusedWindow = focusManager.getFocusedWindow();
    boolean isModalContext = focusedWindow != null && isModalContext(focusedWindow);

    final DataManager dataManager = DataManager.getInstance();
    if (dataManager == null) return false;

    DataContext dataContext = dataManager.getDataContext();

    myContext.setDataContext(dataContext);
    myContext.setFocusOwner(focusOwner);
    myContext.setModalContext(isModalContext);
    myContext.setInputEvent(e);

    if (getState() == KeyState.STATE_INIT) {
      return inInitState();
    } else if (getState() == KeyState.STATE_PROCESSED) {
      return inProcessedState();
    } else if (getState() == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE) {
      return inWaitForSecondStrokeState();
    } else if (getState() == KeyState.STATE_SECOND_STROKE_IN_PROGRESS) {
      return inSecondStrokeInProgressState();
    } else if (getState() == KeyState.STATE_KEY_GESTURE_PROCESSOR) {
      return myKeyGestureProcessor.process();
    } else {
      throw new IllegalStateException("state = " + getState());
    }
  }

  /**
   * @return <code>true</code> if and only if the <code>component</code> represents
   * modal context.
   * @throws IllegalArgumentException if <code>component</code> is <code>null</code>.
   */
  public static boolean isModalContext(@NotNull Component component) {
    Window window;
    if (component instanceof Window) {
      window = (Window)component;
    } else {
      window = SwingUtilities.getWindowAncestor(component);
    }

    if (window instanceof IdeFrameImpl) {
      final Component pane = ((IdeFrameImpl) window).getGlassPane();
      if (pane instanceof IdeGlassPaneEx) {
        return ((IdeGlassPaneEx) pane).isInModalContext();
      }
    }

    if (window instanceof JDialog) {
      final JDialog dialog = (JDialog)window;
      if (!dialog.isModal()) {
        return isModalContext(dialog.getOwner());
      }
    }

    boolean isMainFrame = window instanceof IdeFrameImpl;
    boolean isFloatingDecorator = window instanceof FloatingDecorator;

    boolean isPopup = !(component instanceof JFrame) && !(component instanceof JDialog);
    if (isPopup) {
      if (component instanceof JWindow) {
        JBPopup popup = (JBPopup)((JWindow)component).getRootPane().getClientProperty(AbstractPopup.KEY);
        if (popup != null) {
          return popup.isModalContext();
        }
      }
    }

    return !isMainFrame && !isFloatingDecorator;
  }

  private boolean inWaitForSecondStrokeState() {
    // a key pressed means that the user starts to enter the second stroke...
    if (KeyEvent.KEY_PRESSED==myContext.getInputEvent().getID()) {
      setState(KeyState.STATE_SECOND_STROKE_IN_PROGRESS);
      return inSecondStrokeInProgressState();
    }
    // looks like RELEASEs (from the first stroke) go here...  skip them
    return true;
  }

  /**
   * This is hack. AWT doesn't allow to create KeyStroke with specified key code and key char
   * simultaneously. Therefore we are using reflection.
   */
  private static KeyStroke getKeyStrokeWithoutMouseModifiers(KeyStroke originalKeyStroke){
    int modifier=originalKeyStroke.getModifiers()&~InputEvent.BUTTON1_DOWN_MASK&~InputEvent.BUTTON1_MASK&
                 ~InputEvent.BUTTON2_DOWN_MASK&~InputEvent.BUTTON2_MASK&
                 ~InputEvent.BUTTON3_DOWN_MASK&~InputEvent.BUTTON3_MASK;
    try {
      Method[] methods=AWTKeyStroke.class.getDeclaredMethods();
      Method getCachedStrokeMethod=null;
      for (Method method : methods) {
        if (GET_CACHED_STROKE_METHOD_NAME.equals(method.getName())) {
          getCachedStrokeMethod = method;
          getCachedStrokeMethod.setAccessible(true);
          break;
        }
      }
      if(getCachedStrokeMethod==null){
        throw new IllegalStateException("not found method with name getCachedStrokeMethod");
      }
      Object[] getCachedStrokeMethodArgs=new Object[]{originalKeyStroke.getKeyChar(), originalKeyStroke.getKeyCode(), modifier, originalKeyStroke.isOnKeyRelease()};
      return (KeyStroke)getCachedStrokeMethod.invoke(originalKeyStroke, getCachedStrokeMethodArgs
      );
    }catch(Exception exc){
      throw new IllegalStateException(exc.getMessage());
    }
  }

  private boolean inSecondStrokeInProgressState() {
    KeyEvent e = myContext.getInputEvent();

    // when any key is released, we stop waiting for the second stroke
    if(KeyEvent.KEY_RELEASED==e.getID()){
      myFirstKeyStroke=null;
      setState(KeyState.STATE_INIT);
      Project project = PlatformDataKeys.PROJECT.getData(myContext.getDataContext());
      StatusBar.Info.set(null, project);
      return false;
    }

    KeyStroke originalKeyStroke=KeyStroke.getKeyStrokeForEvent(e);
    KeyStroke keyStroke=getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    updateCurrentContext(myContext.getFoundComponent(), new KeyboardShortcut(myFirstKeyStroke, keyStroke), myContext.isModalContext());

    // consume the wrong second stroke and keep on waiting
    if (myContext.getActions().isEmpty()) {
      return true;
    }

    // finally user had managed to enter the second keystroke, so let it be processed
    Project project = PlatformDataKeys.PROJECT.getData(myContext.getDataContext());
    StatusBarEx statusBar = (StatusBarEx) WindowManager.getInstance().getStatusBar(project);
    if (processAction(e, myActionProcessor)) {
      if (statusBar != null) {
        statusBar.setInfo(null);
      }
      return true;
    } else {
      return false;
    }
  }

  private boolean inProcessedState() {
    KeyEvent e = myContext.getInputEvent();

    // ignore typed events which come after processed pressed event
    if (KeyEvent.KEY_TYPED == e.getID() && isPressedWasProcessed()) {
      return true;
    }
    if (KeyEvent.KEY_RELEASED == e.getID() && KeyEvent.VK_ALT == e.getKeyCode() && isPressedWasProcessed()) {
      //see IDEADEV-8615
      return true;
    }
    setState(KeyState.STATE_INIT);
    setPressedWasProcessed(false);
    return inInitState();
  }

  private boolean inInitState() {
    Component focusOwner = myContext.getFocusOwner();
    boolean isModalContext = myContext.isModalContext();
    DataContext dataContext = myContext.getDataContext();
    KeyEvent e = myContext.getInputEvent();

    // http://www.jetbrains.net/jira/browse/IDEADEV-12372
    if (myLeftCtrlPressed && myRightAltPressed && focusOwner != null && e.getModifiers() == (InputEvent.CTRL_MASK | InputEvent.ALT_MASK)) {
      final InputContext inputContext = focusOwner.getInputContext();
      if (inputContext != null) {
        @NonNls final String language = inputContext.getLocale().getLanguage();
        if (language.equals("pl") ||
            language.equals("de") ||
            language.equals("fi") ||
            language.equals("fr") ||
            language.equals("no") ||
            language.equals("da") ||
            language.equals("se") ||
            language.equals("pt") ||
            language.equals("nl") ||
            language.equals("tr") ||
            language.equals("sl") ||
            language.equals("hu")) {
          // don't search for shortcuts
          return false;
        }
      }
    }

    KeyStroke originalKeyStroke=KeyStroke.getKeyStrokeForEvent(e);
    KeyStroke keyStroke=getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    if (myKeyGestureProcessor.processInitState()) {
      return true;
    }

    if (SystemInfo.isMac) {
      if (e.getModifiersEx() == InputEvent.ALT_DOWN_MASK &&
        (e.getID() == KeyEvent.KEY_PRESSED && hasMnemonicInWindow(focusOwner, e.getKeyCode()) ||
         e.getID() == KeyEvent.KEY_TYPED && hasMnemonicInWindow(focusOwner, e.getKeyChar())))
      {
        setPressedWasProcessed(true);
        setState(KeyState.STATE_PROCESSED);
        return false;
      }
    }

    updateCurrentContext(focusOwner, new KeyboardShortcut(keyStroke, null), isModalContext);
    if(myContext.getActions().isEmpty()) {
      // there's nothing mapped for this stroke
      return false;
    }

    if(myContext.isHasSecondStroke()){
      myFirstKeyStroke=keyStroke;
      ArrayList<Pair<AnAction, KeyStroke>> secondKeyStorkes = new ArrayList<Pair<AnAction,KeyStroke>>();
      for (AnAction action : myContext.getActions()) {
        Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
        for (Shortcut shortcut : shortcuts) {
          if (shortcut instanceof KeyboardShortcut) {
            KeyboardShortcut keyShortcut = (KeyboardShortcut)shortcut;
            if (keyShortcut.getFirstKeyStroke().equals(myFirstKeyStroke)) {
              secondKeyStorkes.add(new Pair<AnAction, KeyStroke>(action, keyShortcut.getSecondKeyStroke()));
            }
          }
        }
      }

      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      StringBuilder message = new StringBuilder();
      message.append(KeyMapBundle.message("prefix.key.pressed.message"));
      message.append(' ');
      for (int i = 0; i < secondKeyStorkes.size(); i++) {
        Pair<AnAction, KeyStroke> pair = secondKeyStorkes.get(i);
        if (i > 0) message.append(", ");
        message.append(pair.getFirst().getTemplatePresentation().getText());
        message.append(" (");
        message.append(KeymapUtil.getKeystrokeText(pair.getSecond()));
        message.append(")");
      }

      StatusBar.Info.set(message.toString(), project);

      mySecondStrokeTimeout.cancelAllRequests();
      mySecondStrokeTimeout.addRequest(mySecondStrokeTimeoutRunnable, Registry.intValue("actionSystem.secondKeystrokeTimout"));

      setState(KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE);
      return true;
    }else{
      return processAction(e, myActionProcessor);
    }
  }

  private static boolean hasMnemonicInWindow(Component focusOwner, int keyCode) {
    if (keyCode == KeyEvent.VK_ALT || keyCode == 0) return false; // Optimization
    final Container container = getContainer(focusOwner);
    return hasMnemonic(container, keyCode);
  }

  @Nullable
  private static Container getContainer(@Nullable final Component focusOwner) {
    if (focusOwner == null) return null;
    if (focusOwner.isLightweight()) {
      Container container = focusOwner.getParent();
      while (container != null) {
        final Container parent = container.getParent();
        if (parent instanceof JLayeredPane) break;
        if (parent != null && parent.isLightweight()) {
          container = parent;
        }
        else {
          break;
        }
      }
      return container;
    }

    return SwingUtilities.windowForComponent(focusOwner);
  }

  private static boolean hasMnemonic(final Container container, final int keyCode) {
    if (container == null) return false;

    final Component[] components = container.getComponents();
    for (Component component : components) {
      if (component instanceof AbstractButton) {
        final AbstractButton button = (AbstractButton)component;
        if (button.getMnemonic() == keyCode) return true;
      }
      if (component instanceof JLabel) {
        final JLabel label = (JLabel)component;
        if (label.getDisplayedMnemonic() == keyCode) return true;
      }
      if (component instanceof Container) {
        if (hasMnemonic((Container)component, keyCode)) return true;
      }
    }
    return false;
  }

  private final ActionProcessor myActionProcessor = new ActionProcessor() {
    public AnActionEvent createEvent(final InputEvent inputEvent, final DataContext context, final String place, final Presentation presentation,
                                     final ActionManager manager) {
      return new AnActionEvent(inputEvent, context, place, presentation, manager, 0);
    }

    public void onUpdatePassed(final InputEvent inputEvent, final AnAction action, final AnActionEvent actionEvent) {
      setState(KeyState.STATE_PROCESSED);
      setPressedWasProcessed(inputEvent.getID() == KeyEvent.KEY_PRESSED);
    }

    public void performAction(final InputEvent e, final AnAction action, final AnActionEvent actionEvent) {
      e.consume();
      action.actionPerformed(actionEvent);
    }
  };

  public boolean processAction(final InputEvent e, ActionProcessor processor) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final Project project = PlatformDataKeys.PROJECT.getData(myContext.getDataContext());
    final boolean dumb = project != null && DumbService.getInstance(project).isDumb();
    List<AnActionEvent> nonDumbAwareAction = new ArrayList<AnActionEvent>();
    for (final AnAction action : myContext.getActions()) {
      final Presentation presentation = myPresentationFactory.getPresentation(action);

      // Mouse modifiers are 0 because they have no any sense when action is invoked via keyboard
      final AnActionEvent actionEvent =
        processor.createEvent(e, myContext.getDataContext(), ActionPlaces.MAIN_MENU, presentation, ActionManager.getInstance());

      ActionUtil.performDumbAwareUpdate(action, actionEvent, true);

      if (dumb && !(action instanceof DumbAware)) {
        if (Boolean.FALSE.equals(presentation.getClientProperty(ActionUtil.WOULD_BE_ENABLED_IF_NOT_DUMB_MODE))) {
          continue;
        }

        nonDumbAwareAction.add(actionEvent);
        continue;
      }

      if (!presentation.isEnabled()) {
        continue;
      }

      processor.onUpdatePassed(e, action, actionEvent);

      ((DataManagerImpl.MyDataContext)myContext.getDataContext()).setEventCount(IdeEventQueue.getInstance().getEventCount());
      actionManager.fireBeforeActionPerformed(action, actionEvent.getDataContext(), actionEvent);
      Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(actionEvent.getDataContext());
      if (component != null && !component.isShowing()) {
        return true;
      }

      processor.performAction(e, action, actionEvent);

      return true;
    }

    if (!nonDumbAwareAction.isEmpty()) {
      showDumbModeWarningLaterIfNobodyConsumesEvent(e, nonDumbAwareAction.toArray(new AnActionEvent[nonDumbAwareAction.size()]));
    }

    return false;
  }

  private static void showDumbModeWarningLaterIfNobodyConsumesEvent(final InputEvent e, final AnActionEvent... actionEvents) {
    if (ModalityState.current() == ModalityState.NON_MODAL) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (e.isConsumed()) return;

            ActionUtil.showDumbModeWarning(actionEvents);
          }
        });
      }
  }

  /**
   * This method fills <code>myActions</code> list.
   * @return true if there is a shortcut with second stroke found.
   */
  public KeyProcessorContext updateCurrentContext(Component component, Shortcut sc, boolean isModalContext){
    myContext.setFoundComponent(null);
    myContext.getActions().clear();

    boolean hasSecondStroke = false;

    // here we try to find "local" shortcuts

    for (; component != null; component = component.getParent()) {
      if (!(component instanceof JComponent)) {
        continue;
      }
      ArrayList listOfActions = (ArrayList)((JComponent)component).getClientProperty(AnAction.ourClientProperty);
      if (listOfActions == null) {
        continue;
      }
      for (Object listOfAction : listOfActions) {
        if (!(listOfAction instanceof AnAction)) {
          continue;
        }
        AnAction action = (AnAction)listOfAction;
        hasSecondStroke |= addAction(action, sc);
      }
      // once we've found a proper local shortcut(s), we continue with non-local shortcuts
      if (!myContext.getActions().isEmpty()) {
        myContext.setFoundComponent((JComponent)component);
        break;
      }
    }

    // search in main keymap

    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    String[] actionIds = keymap.getActionIds(sc);

    ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : actionIds) {
      AnAction action = actionManager.getAction(actionId);
      if (action != null) {
        if (isModalContext && !action.isEnabledInModalContext()) {
          continue;
        }
        hasSecondStroke |= addAction(action, sc);
      }
    }
    myContext.setHasSecondStroke(hasSecondStroke);

    return myContext;
  }

  /**
   * @return true if action is added and has second stroke
   */
  private boolean addAction(AnAction action, Shortcut sc) {
    boolean hasSecondStroke = false;

    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    for (Shortcut each : shortcuts) {
      if (!each.isKeyboard()) continue;
      
      if (each.startsWith(sc)) {
        if (!myContext.getActions().contains(action)) {
          myContext.getActions().add(action);
        }

        if (each instanceof KeyboardShortcut) {
          hasSecondStroke |= ((KeyboardShortcut)each).getSecondKeyStroke() != null;
        }
      }
    }

    return hasSecondStroke;
  }

  public KeyProcessorContext getContext() {
    return myContext;
  }

  public void dispose() {
    myDisposed = true;
  }

  public KeyState getState() {
    return myState;
  }

  public void setState(final KeyState state) {
    myState = state;
    if (myQueue != null) {
      myQueue.maybeReady();
    }
  }

  public void resetState() {
    setState(KeyState.STATE_INIT);
    setPressedWasProcessed(false);
  }

  public boolean isPressedWasProcessed() {
    return myPressedWasProcessed;
  }

  public void setPressedWasProcessed(boolean pressedWasProcessed) {
    myPressedWasProcessed = pressedWasProcessed;
  }

  public boolean isReady() {
    return myState == KeyState.STATE_INIT || myState == KeyState.STATE_PROCESSED;
  }
}
