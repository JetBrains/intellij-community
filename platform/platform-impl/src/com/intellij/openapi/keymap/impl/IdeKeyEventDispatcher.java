// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.diagnostic.EventWatcher;
import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.KeyboardAwareFocusOwner;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.keyGestures.KeyboardGestureProcessor;
import com.intellij.openapi.keymap.impl.ui.ShortcutTextField;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PotemkinOverlayProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ComponentWithMnemonics;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.KeyboardLayoutUtil;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.im.InputContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * This class is automaton with finite number of state.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IdeKeyEventDispatcher {
  private static final Logger LOG = Logger.getInstance(IdeKeyEventDispatcher.class);
  private static final KeyStroke F10 = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);

  private KeyStroke myFirstKeyStroke;
  /**
   * When we "dispatch" key event via keymap, i.e. when registered action has been executed
   * instead of event dispatching, then we have to consume all following KEY_RELEASED and
   * KEY_TYPED event because they are not valid.
   */
  private boolean myPressedWasProcessed;
  private boolean myIgnoreNextKeyTypedEvent;
  private KeyState myState = KeyState.STATE_INIT;

  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private boolean myLeftCtrlPressed;
  private boolean myRightAltPressed;

  private final KeyboardGestureProcessor myKeyGestureProcessor = new KeyboardGestureProcessor(this);

  private final KeyProcessorContext myContext = new KeyProcessorContext();
  private final IdeEventQueue myQueue;

  private final Alarm mySecondStrokeTimeout = new Alarm();

  private final Runnable mySecondStrokeTimeoutRunnable = () -> {
    if (myState == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE) {
      resetState();
      StatusBar.Info.set(null, myContext.getProject());
    }
  };

  public IdeKeyEventDispatcher(@Nullable IdeEventQueue queue) {
    myQueue = queue;
  }

  public boolean isWaitingForSecondKeyStroke() {
    return getState() == KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE || isPressedWasProcessed();
  }

  /**
   * @return {@code true} if and only if the passed event is already dispatched by the
   * {@code IdeKeyEventDispatcher} and there is no need for any other processing of the event.
   */
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      storeAsciiForChar(e);
    }

    if (e.isConsumed()) {
      return false;
    }

    var focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    var focusOwner = focusManager.getFocusOwner();

    if (focusOwner instanceof KeyboardAwareFocusOwner && ((KeyboardAwareFocusOwner)focusOwner).skipKeyEventDispatcher(e)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Key event not processed because %s is in focus and implements %s", focusOwner, KeyboardAwareFocusOwner.class));
      }
      return false;
    }

    int id = e.getID();
    if (myIgnoreNextKeyTypedEvent) {
      if (KeyEvent.KEY_TYPED == id) return true;
      myIgnoreNextKeyTypedEvent = false;
    }

    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && focusOwner instanceof JComponent) {
      SpeedSearchSupply supply = SpeedSearchSupply.getSupply((JComponent)focusOwner);
      if (supply != null && supply.isPopupActive()) {
        return false;
      }
    }

    // http://www.jetbrains.net/jira/browse/IDEADEV-12372 (a.k.a. IDEA-35760)
    if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
      if (id == KeyEvent.KEY_PRESSED) {
        myLeftCtrlPressed = e.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT;
      }
      else if (id == KeyEvent.KEY_RELEASED) {
        myLeftCtrlPressed = false;
      }
    }
    else if (e.getKeyCode() == KeyEvent.VK_ALT) {
      if (id == KeyEvent.KEY_PRESSED) {
        myRightAltPressed = e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT;
      }
      else if (id == KeyEvent.KEY_RELEASED) {
        myRightAltPressed = false;
      }
    }

    // shortcuts should not work in shortcut setup fields
    if (focusOwner instanceof ShortcutTextField) {
      // remove AltGr modifier to show a shortcut without AltGr in Settings
      if (SystemInfoRt.isWindows && KeyEvent.KEY_PRESSED == id) {
        removeAltGraph(e);
      }
      return false;
    }

    if (getState() == KeyState.STATE_INIT && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED &&
        focusOwner instanceof JTextComponent && ((JTextComponent)focusOwner).isEditable()) {
      if (id == KeyEvent.KEY_PRESSED && e.getKeyCode() != KeyEvent.VK_ESCAPE) {
        MacUIUtil.hideCursor();
      }
      if (e.getModifiersEx() == 0 && Character.isLetterOrDigit(e.getKeyChar()) &&
          !Boolean.TRUE.equals(((JTextComponent)focusOwner).getClientProperty(ActionUtil.ALLOW_PlAIN_LETTER_SHORTCUTS))) {
        return false;
      }
    }

    MenuSelectionManager menuSelectionManager=MenuSelectionManager.defaultManager();
    MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
    if(selectedPath.length>0){
      if (!(selectedPath[0] instanceof ComboPopup)) {
        // The following couple of lines of code is a PATCH!!!
        // It is needed to ignore ENTER KEY_TYPED events which sometimes can reach editor when an action
        // is invoked from main menu via Enter key.
        setState(KeyState.STATE_PROCESSED);
        return processMenuActions(e, selectedPath[0]);
      }
    }

    // Keymap shortcuts (i.e. not local shortcuts) should work only in:
    // - main frame
    // - floating focusedWindow
    // - when there's an editor in contexts
    Window focusedWindow = focusManager.getFocusedWindow();
    boolean isModalContext = focusedWindow != null && isModalContext(focusedWindow);

    Application app = ApplicationManager.getApplication();
    DataManager dataManager = app == null ? null : app.getServiceIfCreated(DataManager.class);
    if (dataManager == null) {
      return false;
    }

    DataContext dataContext = dataManager.getDataContext();
    myContext.setDataContext(dataContext);
    myContext.setFocusOwner(focusOwner);
    myContext.setModalContext(isModalContext);
    myContext.setInputEvent(e);
    myContext.setProject(CommonDataKeys.PROJECT.getData(dataContext));

    try {
      switch (getState()) {
        case STATE_INIT:
          return inInitState();
        case STATE_PROCESSED:
          return inProcessedState();
        case STATE_WAIT_FOR_SECOND_KEYSTROKE:
          return inWaitForSecondStrokeState();
        case STATE_SECOND_STROKE_IN_PROGRESS:
          return inSecondStrokeInProgressState();
        case STATE_KEY_GESTURE_PROCESSOR:
          return myKeyGestureProcessor.process();
        case STATE_WAIT_FOR_POSSIBLE_ALT_GR:
          return inWaitForPossibleAltGr();
        default:
          throw new IllegalStateException("state = " + getState());
      }
    }
    finally {
      myContext.clear();
    }
  }

  private static void storeAsciiForChar(@NotNull KeyEvent e) {
    char aChar = e.getKeyChar();
    if (aChar == KeyEvent.CHAR_UNDEFINED) return;
    int mods = e.getModifiers();
    if ((mods & ~InputEvent.SHIFT_MASK & ~InputEvent.SHIFT_DOWN_MASK) != 0) return;

    KeyboardLayoutUtil.storeAsciiForChar(e.getKeyCode(), aChar, KeyEvent.VK_A, KeyEvent.VK_Z);
  }

  /**
   * @return {@code true} if and only if the {@code component} represents
   * modal context.
   * @throws IllegalArgumentException if {@code component} is {@code null}.
   */
  public static boolean isModalContext(@NotNull Component component) {
    Window window = ComponentUtil.getWindow(component);

    if (window instanceof IdeFrameImpl) {
      Component pane = ((JFrame)window).getGlassPane();
      if (pane instanceof IdeGlassPaneEx) {
        return ((IdeGlassPaneEx) pane).isInModalContext();
      }
    }

    if (window instanceof JDialog) {
      final JDialog dialog = (JDialog)window;
      if (!dialog.isModal()) {
        final Window owner = dialog.getOwner();
        return owner != null && isModalContext(owner);
      }
    }

    if (window instanceof JFrame) {
      return false;
    }

    boolean isFloatingDecorator = window instanceof FloatingDecorator;

    boolean isPopup = !(component instanceof JFrame) && !(component instanceof JDialog);
    if (isPopup) {
      if (component instanceof JWindow) {
        JBPopup popup = (JBPopup)((JWindow)component).getRootPane().getClientProperty(JBPopup.KEY);
        if (popup != null) {
          return popup.isModalContext();
        }
      }
    }

    return !isFloatingDecorator;
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

  private static final class ReflectionHolder {
    static final Method getCachedStroke = Objects.requireNonNull(
      ReflectionUtil.getDeclaredMethod(AWTKeyStroke.class, "getCachedStroke", char.class, int.class, int.class, boolean.class));
  }

  /**
   * This is hack. AWT doesn't allow to create KeyStroke with specified key code and key char
   * simultaneously. Therefore we are using reflection.
   */
  private static KeyStroke getKeyStrokeWithoutMouseModifiers(@NotNull KeyStroke originalKeyStroke){
    int modifier=originalKeyStroke.getModifiers()&~InputEvent.BUTTON1_DOWN_MASK&~InputEvent.BUTTON1_MASK&
                 ~InputEvent.BUTTON2_DOWN_MASK&~InputEvent.BUTTON2_MASK&
                 ~InputEvent.BUTTON3_DOWN_MASK&~InputEvent.BUTTON3_MASK;
    try {
      return (KeyStroke)ReflectionHolder.getCachedStroke.invoke(originalKeyStroke,
                                                                originalKeyStroke.getKeyChar(), originalKeyStroke.getKeyCode(),
                                                                modifier, originalKeyStroke.isOnKeyRelease());
    }
    catch(Exception exc){
      throw new IllegalStateException(exc.getMessage());
    }
  }

  private boolean inWaitForPossibleAltGr() {
    KeyEvent e = myContext.getInputEvent();
    KeyStroke keyStroke = myFirstKeyStroke;
    myFirstKeyStroke = null;
    setState(KeyState.STATE_INIT);

    // processing altGr
    int eventId = e.getID();
    if (KeyEvent.KEY_TYPED == eventId && e.isAltGraphDown()) {
      return false;
    }
    else if (KeyEvent.KEY_RELEASED == eventId) {
      updateCurrentContext(myContext.getFoundComponent(), new KeyboardShortcut(keyStroke, null));

      if (myContext.getActions().isEmpty()) {
        return false;
      }

      return processActionOrWaitSecondStroke(keyStroke);
    }

    return false;
  }

  private boolean inSecondStrokeInProgressState() {
    KeyEvent e = myContext.getInputEvent();

    // when any key is released, we stop waiting for the second stroke
    if (KeyEvent.KEY_RELEASED == e.getID()) {
      myFirstKeyStroke = null;
      setState(KeyState.STATE_INIT);
      Project project = myContext.getProject();
      StatusBar.Info.set(null, project);
      return false;
    }

    KeyStroke originalKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e);
    if (originalKeyStroke == null) {
      return false;
    }

    KeyStroke keyStroke = getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    updateCurrentContext(myContext.getFoundComponent(), new KeyboardShortcut(myFirstKeyStroke, keyStroke));

    // consume the wrong second stroke and keep on waiting
    if (myContext.getActions().isEmpty()) {
      return true;
    }

    if (processAction(e, myActionProcessor)) {
      StatusBar.Info.set(null, myContext.getProject());
      return true;
    }
    else {
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
    KeyEvent e = myContext.getInputEvent();

    if (SystemInfoRt.isWindows && KeyEvent.KEY_PRESSED == e.getID() && removeAltGraph(e) && e.isControlDown()) {
      myFirstKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e);
      if (myFirstKeyStroke == null) return false;
      setState(KeyState.STATE_WAIT_FOR_POSSIBLE_ALT_GR);
      return true;
    }
    // http://www.jetbrains.net/jira/browse/IDEADEV-12372 (a.k.a. IDEA-35760)
    boolean isCandidateForAltGr = myLeftCtrlPressed &&
                                  myRightAltPressed &&
                                  focusOwner != null &&
                                  ( (e.getModifiers() & ~InputEvent.SHIFT_MASK) == (InputEvent.CTRL_MASK | InputEvent.ALT_MASK) );
    if (isCandidateForAltGr) {
      if (Registry.is("actionSystem.force.alt.gr", false)) {
        return false;
      }
      if (isAltGrLayout(focusOwner)) {
        return false; // don't search for shortcuts
      }
    }

    KeyStroke originalKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(e);
    if (originalKeyStroke == null) {
      return false;
    }
    KeyStroke keyStroke = getKeyStrokeWithoutMouseModifiers(originalKeyStroke);

    if (myKeyGestureProcessor.processInitState()) {
      return true;
    }

    if (InputEvent.ALT_DOWN_MASK == e.getModifiersEx() && (!SystemInfo.isMac || Registry.is("ide.mac.alt.mnemonic.without.ctrl"))) {
      // the myIgnoreNextKeyTypedEvent changes event processing to support Alt-based mnemonics
      if ((KeyEvent.KEY_TYPED == e.getID() && !IdeEventQueue.getInstance().isInputMethodEnabled()) ||
          hasMnemonicInWindow(focusOwner, e)) {
        myIgnoreNextKeyTypedEvent = true;
        return false;
      }
    }

    updateCurrentContext(focusOwner, new KeyboardShortcut(keyStroke, null));
    if (myContext.getActions().isEmpty()) {
      // there's nothing mapped for this stroke
      return false;
    }

    // workaround for IDEA-177327
    if (isCandidateForAltGr && SystemInfo.isWindows && Registry.is("actionSystem.fix.alt.gr")) {
      myFirstKeyStroke = keyStroke;
      setState(KeyState.STATE_WAIT_FOR_POSSIBLE_ALT_GR);
      return true;
    }

    return processActionOrWaitSecondStroke(keyStroke) ||
           // We mute standard L&F behaviour on F10 (focusing menu) if some IDE action is bound to F10,
           // even if that action is currently disabled. Opposite behaviour turns out to be inconvenient,
           // at least for 'Visual Studio' keymap, where F10 is bound to 'Step Over' (see IDEA-138429).
           F10.equals(keyStroke);
  }

  private boolean processActionOrWaitSecondStroke(KeyStroke keyStroke) {
    if (!myContext.getSecondStrokeActions().isEmpty()) {
      myFirstKeyStroke = keyStroke;
    }
    return processAction(myContext.getInputEvent(), myActionProcessor);
  }

  private void waitSecondStroke(@NotNull AnAction chosenAction, @NotNull Presentation presentation) {
    StatusBar.Info.set(getSecondStrokeMessage(chosenAction, presentation), myContext.getProject());
    mySecondStrokeTimeout.cancelAllRequests();
    mySecondStrokeTimeout.addRequest(mySecondStrokeTimeoutRunnable, Registry.intValue("actionSystem.secondKeystrokeTimeout"));
    setState(KeyState.STATE_WAIT_FOR_SECOND_KEYSTROKE);
  }

  @NlsContexts.StatusBarText
  private @NotNull String getSecondStrokeMessage(@NotNull AnAction chosenAction, @NotNull Presentation presentation) {
    @NlsContexts.StatusBarText StringBuilder message = new StringBuilder();
    message.append(KeyMapBundle.message("prefix.key.pressed.message"));
    message.append(": ");
    message.append(presentation.getText());
    message.append(" (");
    message.append(KeymapUtil.getKeystrokeText(getSecondKeystroke(chosenAction, myFirstKeyStroke)));
    message.append(")");
    for (AnAction action : myContext.getSecondStrokeActions()) {
      if (action == chosenAction) continue;
      message.append(", ");
      message.append(action.getTemplatePresentation().getText());
      message.append(" (");
      message.append(KeymapUtil.getKeystrokeText(getSecondKeystroke(action, myFirstKeyStroke)));
      message.append(")");
    }
    return message.toString();
  }

  private static @Nullable KeyStroke getSecondKeystroke(@NotNull AnAction action, @NotNull KeyStroke firstKeyStroke) {
    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyShortcut = (KeyboardShortcut)shortcut;
        if (firstKeyStroke.equals(keyShortcut.getFirstKeyStroke())) {
          KeyStroke secondKeyStroke = keyShortcut.getSecondKeyStroke();
          if (secondKeyStroke != null) return secondKeyStroke;
        }
      }
    }
    return null;
  }

  public static boolean hasMnemonicInWindow(Component focusOwner, @NotNull KeyEvent event) {
    return KeyEvent.KEY_TYPED == event.getID() && hasMnemonicInWindow(focusOwner, event.getKeyChar()) ||
           KeyEvent.KEY_PRESSED == event.getID() && hasMnemonicInWindow(focusOwner, event.getKeyCode());
  }

  private static boolean hasMnemonicInWindow(Component focusOwner, int keyCode) {
    if (keyCode == KeyEvent.VK_ALT || keyCode == 0) return false; // Optimization
    Container container = focusOwner == null ? null : ComponentUtil.getWindow(focusOwner);
    if (container instanceof JFrame) {
      ComponentWithMnemonics componentWithMnemonics = ComponentUtil.getParentOfType(ComponentWithMnemonics.class, focusOwner);
      if (componentWithMnemonics instanceof Container) {
        container = (Container)componentWithMnemonics;
      }
    }
    return hasMnemonic(container, keyCode) || hasMnemonicInBalloons(container, keyCode);
  }

  private static boolean hasMnemonic(@Nullable Container container, int keyCode) {
    Component component = UIUtil.uiTraverser(container)
      .traverse()
      .filter(Component::isEnabled)
      .filter(Component::isShowing)
      .find(c -> !(c instanceof ActionMenu) && MnemonicHelper.hasMnemonic(c, keyCode));
    return component != null;
  }

  private static boolean hasMnemonicInBalloons(Container container, int code) {
    Component parent = UIUtil.findUltimateParent(container);
    if (parent instanceof RootPaneContainer) {
      final JLayeredPane pane = ((RootPaneContainer)parent).getLayeredPane();
      for (Component component : pane.getComponents()) {
        if (component instanceof ComponentWithMnemonics &&
            component instanceof Container &&
            hasMnemonic((Container)component, code)) {
          return true;
        }
      }
    }
    return false;
  }

  private final ActionProcessor myActionProcessor = new ActionProcessor() {
    @NotNull
    @Override
    public AnActionEvent createEvent(@NotNull InputEvent inputEvent,
                                     @NotNull DataContext context,
                                     @NotNull String place,
                                     @NotNull Presentation presentation,
                                     @NotNull ActionManager manager) {
      // Mouse modifiers are 0 because they have no any sense when action is invoked via keyboard
      return new AnActionEvent(inputEvent, context, place, presentation, manager, 0);
    }

    @Override
    public void onUpdatePassed(@NotNull InputEvent inputEvent, @NotNull AnAction action, @NotNull AnActionEvent event) {
      setState(KeyState.STATE_PROCESSED);
      setPressedWasProcessed(inputEvent.getID() == KeyEvent.KEY_PRESSED);
    }

    @Override
    public void performAction(@NotNull InputEvent inputEvent, @NotNull AnAction action, @NotNull AnActionEvent event) {
      try {
        super.performAction(inputEvent, action, event);
      }
      finally {
        if (Registry.is("actionSystem.fixLostTyping")) {
          IdeEventQueue.getInstance().doWhenReady(() -> IdeEventQueue.getInstance().getKeyEventDispatcher().resetState());
        }
      }
    }
  };

  public boolean processAction(@NotNull InputEvent e, @NotNull ActionProcessor processor) {
    return processAction(e, ActionPlaces.KEYBOARD_SHORTCUT, myContext.getDataContext(),
                         new ArrayList<>(myContext.getActions()), processor, myPresentationFactory, myContext.getShortcut());
  }

  boolean processAction(@NotNull InputEvent e,
                        @NotNull String place,
                        @NotNull DataContext context,
                        @NotNull List<AnAction> actions,
                        @NotNull ActionProcessor processor,
                        @NotNull PresentationFactory presentationFactory,
                        @NotNull Shortcut shortcut) {
    if (actions.isEmpty()) return false;
    DataContext wrappedContext = Utils.wrapDataContext(context);
    Project project = CommonDataKeys.PROJECT.getData(wrappedContext);
    boolean dumb = project != null && DumbService.getInstance(project).isDumb();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(AnActionListener.TOPIC)
      .beforeShortcutTriggered(shortcut, Collections.unmodifiableList(actions), context);

    List<AnAction> wouldBeEnabledIfNotDumb = ContainerUtil.createLockFreeCopyOnWriteList();
    ProgressIndicator indicator = Registry.is("actionSystem.update.actions.cancelable.beforeActionPerformedUpdate") ?
                                  new PotemkinOverlayProgress(PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(wrappedContext)) :
                                  new ProgressIndicatorBase();
    Pair<Trinity<AnAction, AnActionEvent, Long>, Boolean> chosenPair = ProgressManager.getInstance().runProcess(() -> {
      Map<Presentation, AnActionEvent> events = new ConcurrentHashMap<>();
      Trinity<AnAction, AnActionEvent, Long> chosen = Utils.runUpdateSessionForInputEvent(
        e, wrappedContext, place, processor, presentationFactory,
        event -> events.put(event.getPresentation(), event),
        session -> Utils.tryInReadAction(
          () -> rearrangeByPromoters(actions, Utils.freezeDataContext(wrappedContext, null))) ?
                   doUpdateActionsInner(actions, dumb, wouldBeEnabledIfNotDumb, session, events::get) : null);
      if (chosen == null) return null;

      if (!myContext.getSecondStrokeActions().contains(chosen.first)) {
        AnActionEvent actionEvent = chosen.second.withDataContext(wrappedContext); // use not frozen data context
        if (!ActionUtil.lastUpdateAndCheckDumb(chosen.first, actionEvent, false)) {
          LOG.warn("Action '" + actionEvent.getPresentation().getText() + "' (" + chosen.first.getClass() + ") " +
                   "has become disabled in `beforeActionPerformedUpdate` right after successful `update`");
          logTimeMillis(chosen.third, chosen.first);
        }
        else {
          return Pair.create(Trinity.create(chosen.first, actionEvent, chosen.third), true);
        }
      }
      return Pair.create(chosen, false);
    }, indicator);

    Trinity<AnAction, AnActionEvent, Long> chosen = chosenPair != null ? chosenPair.first : null;
    boolean doPerform = chosen != null && chosenPair.second;
    boolean hasSecondStroke = chosen != null && myContext.getSecondStrokeActions().contains(chosen.first);

    if (e.getID() == KeyEvent.KEY_PRESSED && !hasSecondStroke && (chosen != null || !wouldBeEnabledIfNotDumb.isEmpty())) {
      myIgnoreNextKeyTypedEvent = true;
    }

    if (doPerform) {
      doPerformActionInner(e, processor, context, chosen.first, chosen.second);
      logTimeMillis(chosen.third, chosen.first);
    }
    else if (hasSecondStroke) {
      waitSecondStroke(chosen.first, chosen.second.getPresentation());
    }
    else if (!wouldBeEnabledIfNotDumb.isEmpty()) {
      showDumbModeBalloonLater(project, getActionUnavailableMessage(wouldBeEnabledIfNotDumb), () -> {
        //invokeLater to make sure correct dataContext is taken from focus
        ApplicationManager.getApplication().invokeLater(() ->
          DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(ctx ->
            processAction(e, place, ctx, actions, processor, presentationFactory, shortcut)
          )
        );
      }, __ -> e.isConsumed());
    }
    return chosen != null;
  }

  @Nullable
  private static Trinity<AnAction, AnActionEvent, Long> doUpdateActionsInner(@NotNull List<AnAction> actions,
                                                                             boolean dumb,
                                                                             @NotNull List<? super AnAction> wouldBeEnabledIfNotDumb,
                                                                             @NotNull UpdateSession session,
                                                                             @NotNull Function<? super Presentation, ? extends AnActionEvent> events) {
    for (AnAction action : actions) {
      long startedAt = System.currentTimeMillis();
      Presentation presentation = session.presentation(action);
      if (dumb && !action.isDumbAware()) {
        if (!Boolean.FALSE.equals(presentation.getClientProperty(ActionUtil.WOULD_BE_ENABLED_IF_NOT_DUMB_MODE))) {
          wouldBeEnabledIfNotDumb.add(action);
        }
        logTimeMillis(startedAt, action);
        continue;
      }
      AnActionEvent event = events.apply(presentation);
      if (event == null || !presentation.isEnabled()) {
        logTimeMillis(startedAt, action);
        continue;
      }
      return Trinity.create(action, event, startedAt);
    }
    return null;
  }

  private static void doPerformActionInner(@NotNull InputEvent e,
                                           @NotNull ActionProcessor processor,
                                           @NotNull DataContext context,
                                           @NotNull AnAction action,
                                           @NotNull AnActionEvent actionEvent) {
    processor.onUpdatePassed(e, action, actionEvent);

    int eventCount = IdeEventQueue.getInstance().getEventCount();
    if (context instanceof EdtDataContext) { // this is not true for test data contexts
      ((EdtDataContext)context).setEventCount(eventCount);
    }
    ActionUtil.performDumbAwareWithCallbacks(action, actionEvent, () -> {
      LOG.assertTrue(eventCount == IdeEventQueue.getInstance().getEventCount(),
                     "Event counts do not match: " + eventCount + " != " + IdeEventQueue.getInstance().getEventCount());
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> processor.performAction(e, action, actionEvent));
    });
  }

  private static void showDumbModeBalloonLater(@Nullable Project project,
                                               @NotNull @Nls String message,
                                               @NotNull Runnable retryRunnable,
                                               @NotNull Condition<Object> expired) {
    if (project == null || expired.value(null)) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (expired.value(null)) return;
      DumbService.getInstance(project).showDumbModeActionBalloon(message, retryRunnable);
    }, Conditions.or(expired, project.getDisposed()));
  }

  private static @NotNull @Nls String getActionUnavailableMessage(@NotNull List<? extends AnAction> actions) {
    List<String> actionNames = new ArrayList<>();
    for (AnAction action : actions) {
      String s = action.getTemplateText();
      if (Strings.isNotEmpty(s)) {
        actionNames.add(s);
      }
    }
    ContainerUtil.removeDuplicates(actionNames);
    if (actionNames.isEmpty()) {
      return getUnavailableMessage(IdeBundle.message("dumb.balloon.this.action"), false);
    }
    else if (actionNames.size() == 1) {
      return getUnavailableMessage("'" + actionNames.get(0) + "'", false);
    }
    else {
      @NlsSafe String join = String.join(", ", actionNames);
      return getUnavailableMessage(IdeBundle.message("dumb.balloon.none.of.the.following.actions"), true) +
             ": " + join;
    }
  }

  public static @NotNull @Nls String getUnavailableMessage(@NotNull @Nls String action, boolean plural) {
    return plural ? IdeBundle.message("dumb.balloon.0.are.not.available.while.indexing", action) :
           IdeBundle.message("dumb.balloon.0.is.not.available.while.indexing", action);
  }

  /**
   * This method fills {@code myActions} list.
   */
  private KeyEvent lastKeyEventForCurrentContext;

  public void updateCurrentContext(@Nullable Component component, @NotNull Shortcut sc) {
    myContext.setFoundComponent(null);
    myContext.getSecondStrokeActions().clear();
    myContext.getActions().clear();
    myContext.setShortcut(null);

    if (Registry.is("ide.edt.update.context.only.on.key.pressed.event")) {
      KeyEvent keyEvent = myContext.getInputEvent();
      if (keyEvent == null || keyEvent.getID() != KeyEvent.KEY_PRESSED) return;
      if (keyEvent == lastKeyEventForCurrentContext) return;
      lastKeyEventForCurrentContext = keyEvent;
    }

    if (isControlEnterOnDialog(component, sc)) return;

    // here we try to find "local" shortcuts
    for (; component != null; component = component.getParent()) {
      if (!(component instanceof JComponent)) {
        continue;
      }
      List<AnAction> listOfActions = ActionUtil.getActions((JComponent)component);
      if (listOfActions.isEmpty()) {
        continue;
      }
      for (AnAction action : listOfActions) {
        addAction(action, sc);
      }
      // once we've found a proper local shortcut(s), we continue with non-local shortcuts
      if (!myContext.getActions().isEmpty()) {
        myContext.setFoundComponent((JComponent)component);
        break;
      }
    }

    addActionsFromActiveKeymap(sc);

    if (myContext.getSecondStrokeActions().isEmpty() && sc instanceof KeyboardShortcut) {
      // little trick to invoke action which second stroke is a key w/o modifiers, but user still
      // holds the modifier key(s) of the first stroke

      final KeyboardShortcut keyboardShortcut = (KeyboardShortcut)sc;
      final KeyStroke firstKeyStroke = keyboardShortcut.getFirstKeyStroke();
      final KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();

      if (secondKeyStroke != null && secondKeyStroke.getModifiers() != 0 && firstKeyStroke.getModifiers() != 0) {
        final KeyboardShortcut altShortCut = new KeyboardShortcut(firstKeyStroke, KeyStroke.getKeyStroke(secondKeyStroke.getKeyCode(), 0));
        addActionsFromActiveKeymap(altShortCut);
      }
    }
  }

  private static boolean rearrangeByPromoters(@NotNull List<AnAction> actions, @NotNull DataContext context) {
    List<AnAction> readOnlyActions = Collections.unmodifiableList(actions);
    List<ActionPromoter> promoters = ContainerUtil.concat(
      ActionPromoter.EP_NAME.getExtensionList(), ContainerUtil.filterIsInstance(actions, ActionPromoter.class));
    for (ActionPromoter promoter : promoters) {
      try {
        List<AnAction> promoted = promoter.promote(readOnlyActions, context);
        if (promoted != null && !promoted.isEmpty()) {
          actions.removeAll(promoted);
          actions.addAll(0, promoted);
        }
        List<AnAction> suppressed = promoter.suppress(readOnlyActions, context);
        if (suppressed != null && !suppressed.isEmpty()) {
          actions.removeAll(suppressed);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return true;
  }

  private void addActionsFromActiveKeymap(@NotNull Shortcut shortcut) {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred()) {
      return;
    }

    ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (actionManager == null) {
      return;
    }

    KeymapManager keymapManager = KeymapManager.getInstance();
    Keymap keymap = keymapManager == null ? null : keymapManager.getActiveKeymap();
    String[] actionIds = keymap == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : keymap.getActionIds(shortcut);
    for (String actionId : actionIds) {
      AnAction action = actionManager.getAction(actionId);
      if (action != null && (!myContext.isModalContext() || action.isEnabledInModalContext())) {
        addAction(action, shortcut);
      }
    }

    if (keymap != null && actionIds.length > 0 && shortcut instanceof KeyboardShortcut) {
      // user pressed keystroke and keymap has some actions assigned to sc (actions going to be executed)
      // check whether this shortcut conflicts with system-wide shortcuts and notify user if necessary
      // see IDEA-173174 Warn user about IDE keymap conflicts with native OS keymap
      SystemShortcuts.getInstance().onUserPressedShortcut(keymap, actionIds, (KeyboardShortcut)shortcut);
    }
  }

  private static final KeyboardShortcut CONTROL_ENTER = KeyboardShortcut.fromString("control ENTER");
  private static final KeyboardShortcut CMD_ENTER = KeyboardShortcut.fromString("meta ENTER");
  private static boolean isControlEnterOnDialog(Component component, Shortcut sc) {
    return (CONTROL_ENTER.equals(sc) || (SystemInfo.isMac && CMD_ENTER.equals(sc)))
           && !IdeEventQueue.getInstance().isPopupActive() //avoid Control+Enter in completion
           && DialogWrapper.findInstance(component) != null;
  }

  private void addAction(@NotNull AnAction action, @NotNull Shortcut sc) {
    if (action instanceof EmptyAction) return;
    boolean isNotTwoStroke = sc instanceof KeyboardShortcut && ((KeyboardShortcut)sc).getSecondKeyStroke() == null;
    for (Shortcut each : action.getShortcutSet().getShortcuts()) {
      if (each == null) {
        throw new NullPointerException("unexpected shortcut of action: " + action);
      }
      if (!each.isKeyboard()) {
        continue;
      }
      if (each.startsWith(sc)) {
        if (each instanceof KeyboardShortcut && ((KeyboardShortcut)each).getSecondKeyStroke() != null &&
            isNotTwoStroke && !myContext.getSecondStrokeActions().contains(action)) {
          myContext.getSecondStrokeActions().add(action);
        }
        if (!myContext.getActions().contains(action)) {
          myContext.getActions().add(action);
          myContext.setShortcut(sc);
        }
      }
    }
  }

  @ApiStatus.Internal
  public @NotNull KeyProcessorContext getContext() {
    return myContext;
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

  private void resetState() {
    setState(KeyState.STATE_INIT);
    setPressedWasProcessed(false);
  }

  public boolean isPressedWasProcessed() {
    return myPressedWasProcessed;
  }

  private void setPressedWasProcessed(boolean pressedWasProcessed) {
    myPressedWasProcessed = pressedWasProcessed;
  }

  public boolean isReady() {
    return myState == KeyState.STATE_INIT || myState == KeyState.STATE_PROCESSED;
  }


  private static final String POPUP_MENU_PREFIX = "PopupMenu-"; // see PlatformActions.xml

  private static boolean processMenuActions(@NotNull KeyEvent event, MenuElement element) {
    if (KeyEvent.KEY_PRESSED != event.getID() || !Registry.is("ide.popup.navigation.via.actions")) {
      return false;
    }

    KeymapManager manager = KeymapManager.getInstance();
    if (manager == null) {
      return false;
    }

    JRootPane pane = getMenuActionsHolder(element.getComponent());
    if (pane == null) {
      return false;
    }

    Keymap keymap = manager.getActiveKeymap();
    // iterate through actions for the specified event
    for (String id : keymap.getActionIds(KeyStroke.getKeyStrokeForEvent(event))) {
      if (id.startsWith(POPUP_MENU_PREFIX)) {
        String actionId = id.substring(POPUP_MENU_PREFIX.length());
        Action action = pane.getActionMap().get(actionId);
        if (action != null) {
          action.actionPerformed(new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, actionId));
          event.consume();
          return true; // notify dispatcher that event is processed
        }
      }
    }
    return false;
  }

  private static JRootPane getMenuActionsHolder(Component component) {
    if (component instanceof JPopupMenu) {
      // BasicPopupMenuUI.MenuKeyboardHelper#stateChanged
      JPopupMenu menu = (JPopupMenu)component;
      return SwingUtilities.getRootPane(menu.getInvoker());
    }
    return SwingUtilities.getRootPane(component);
  }

  private static void logTimeMillis(long startedAt, @NotNull AnAction action) {
    EventWatcher watcher = EventWatcher.getInstanceOrNull();
    if (watcher != null) {
      watcher.logTimeMillis(action.toString(), startedAt);
    }
  }

  public static boolean removeAltGraph(@NotNull InputEvent e) {
    if (e.isAltGraphDown()) {
      try {
        Field field = InputEvent.class.getDeclaredField("modifiers");
        field.setAccessible(true);
        field.setInt(e, ~InputEvent.ALT_GRAPH_MASK & ~InputEvent.ALT_GRAPH_DOWN_MASK & field.getInt(e));
        return true;
      }
      catch (Exception ignored) {
      }
    }
    return false;
  }

  public static boolean isAltGrLayout(Component component) {
    InputContext context = component == null ? null : component.getInputContext();
    Locale locale = context == null ? null : context.getLocale();
    if (locale == null) {
      return false;
    }

    String language = locale.getLanguage();
    boolean contains = !"en".equals(language)
                       ? ALT_GR_LANGUAGES.contains(language)
                       : ALT_GR_COUNTRIES.contains(locale.getCountry());
    LOG.debug("AltGr", contains ? "" : " not", " supported for ", locale);
    return contains;
  }

  // http://www.oracle.com/technetwork/java/javase/documentation/jdk12locales-5294582.html
  @NonNls private static final Set<String> ALT_GR_LANGUAGES = Set.of(
    "da", // Danish
    "de", // German
    "es", // Spanish
    "et", // Estonian
    "fi", // Finnish
    "fr", // French
    "hr", // Croatian
    "hu", // Hungarian
    "it", // Italian
    "lv", // Latvian
    "mk", // Macedonian
    "nl", // Dutch
    "no", // Norwegian
    "pl", // Polish
    "pt", // Portuguese
    "ro", // Romanian
    "sk", // Slovak
    "sl", // Slovenian
    "sr", // Serbian
    "sv", // Swedish
    "tr"  // Turkish
  );

  @NonNls private static final Set<String> ALT_GR_COUNTRIES = Set.of(
    "DK", // Denmark
    "DE", // Germany
    "FI", // Finland
    "NL", // Netherlands
    "SL", // Slovenia
    "SE"  // Sweden
  );
}