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
package com.intellij.ui.mac;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import org.jetbrains.annotations.Nullable;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import static com.intellij.ui.mac.foundation.Foundation.*;
import static com.intellij.ui.mac.foundation.Foundation.invoke;

/**
 * @author pegov
 */
public class MacMessagesImpl extends MacMessages {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.MacMessages");

  private static final  HashMap<Window, Integer> resultsFromDocumentRoot = new HashMap<Window, Integer> ();
  private static final  HashMap<Window, MacMessagesQueue<Runnable>> queuesFromDocumentRoot =
    new HashMap<Window, MacMessagesQueue<Runnable>>();

  private static final Callback SHEET_DID_END = new Callback() {
    public void callback(ID self, String selector, ID alert, ID returnCode, ID contextInfo) {
      cfRelease(self);
      synchronized (lock) {
        Window documentRoot = windowFromId.get(contextInfo.longValue());
        processResult(documentRoot);
        resultsFromDocumentRoot.put(documentRoot, returnCode.intValue());
        queuesFromDocumentRoot.get(windowFromId.get(contextInfo.longValue())).runFromQueue();
      }
    }
  };

  private static final Callback VARIABLE_BUTTONS_SHEET_PANEL = new Callback() {
    public void callback(ID self, String selector, ID params) {
      ID title = invoke(params, "objectAtIndex:", 0);
      ID message = invoke(params, "objectAtIndex:", 1);
      ID focusedWindow = invoke(params, "objectAtIndex:", 2);
      ID alertStyle = invoke(params, "objectAtIndex:", 4);
      ID doNotAskText = invoke(params, "objectAtIndex:", 5);
      int defaultOptionIndex = Integer.parseInt(toStringViaUTF8(invoke(params, "objectAtIndex:", 6)));
      int focusedOptionIndex = Integer.parseInt(toStringViaUTF8(invoke(params, "objectAtIndex:", 7)));
      ID buttons = invoke(params, "objectAtIndex:", 8);
      ID doNotAskChecked = invoke(params, "objectAtIndex:", 9);

      ID alert = invoke(invoke("NSAlert", "alloc"), "init");

      invoke(alert, "setMessageText:", title);
      invoke(alert, "setInformativeText:", message);

      if ("error".equals(toStringViaUTF8(alertStyle))) {
        invoke(alert, "setAlertStyle:", 2); // NSCriticalAlertStyle = 2
      }

      final ID buttonEnumerator = invoke(buttons, "objectEnumerator");
      while (true) {
        final ID button = invoke(buttonEnumerator, "nextObject");
        if (0 == button.intValue()) break;
        invoke(alert, "addButtonWithTitle:", button);
      }

      if (defaultOptionIndex != -1) {
        invoke(invoke(alert, "window"), "setDefaultButtonCell:",
               invoke(invoke(invoke(alert, "buttons"), "objectAtIndex:", defaultOptionIndex), "cell"));
      }

      // it seems like asking for focus will cause java to go and query focus owner too, which may cause dead locks on main-thread
      //if (focusedOptionIndex != -1) {
      //  invoke(invoke(alert, "window"), "makeFirstResponder:",
      //         invoke(invoke(alert, "buttons"), "objectAtIndex:", focusedOptionIndex));
      //} else {
      //  int count = invoke(buttons, "count").intValue();
      //  invoke(invoke(alert, "window"), "makeFirstResponder:",
      //         invoke(invoke(alert, "buttons"), "objectAtIndex:", count == 1 ? 0 : 1));
      //}

      String doNotAsk = toStringViaUTF8(doNotAskText);
      if (!"-1".equals(doNotAsk)) {
        invoke(alert, "setShowsSuppressionButton:", 1);
        invoke(invoke(alert, "suppressionButton"), "setTitle:", doNotAskText);
        invoke(invoke(alert, "suppressionButton"), "setState:", "checked".equals(toStringViaUTF8(doNotAskChecked)));
      }

      invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
             createSelector("alertDidEnd:returnCode:contextInfo:"), focusedWindow);
    }
  };

  private static final Callback SIMPLE_SHEET_PANEL = new Callback() {
    public void callback(ID self, String selector, ID params) {
      ID title = invoke(params, "objectAtIndex:", 0);
      ID defaultText = invoke(params, "objectAtIndex:", 1);
      ID otherText = invoke(params, "objectAtIndex:", 2);
      ID alternateText = invoke(params, "objectAtIndex:", 3);
      ID message = invoke(params, "objectAtIndex:", 4);
      ID focusedWindow = invoke(params, "objectAtIndex:", 5);
      ID alertStyle = invoke(params, "objectAtIndex:", 7);
      ID doNotAskText = invoke(params, "objectAtIndex:", 8);
      ID doNotAskChecked = invoke(params, "objectAtIndex:", 9);

      boolean alternateExist = !"-1".equals(toStringViaUTF8(alternateText));
      boolean otherExist = !"-1".equals(toStringViaUTF8(otherText));

      final ID alert = invoke("NSAlert", "alertWithMessageText:defaultButton:alternateButton:otherButton:informativeTextWithFormat:",
                              title, defaultText, alternateExist ? alternateText : null, otherExist ? otherText : null, message);

      if ("error".equals(toStringViaUTF8(alertStyle))) {
        invoke(alert, "setAlertStyle:", 2); // NSCriticalAlertStyle = 2
      }

      // it seems like asking for focus will cause java to go and query focus owner too, which may cause dead locks on main-thread
      //ID window = invoke(alert, "window");
      //invoke(window, "makeFirstResponder:",
      //       invoke(invoke(alert, "buttons"), "objectAtIndex:", alternateExist ? 2 : otherExist ? 1 : 0));
      //
      ////it is impossible to override ESCAPE key behavior -> key should be named "Cancel" to be bound to ESC
      //if (!alternateExist) {
      //  invoke(invoke(invoke(alert, "buttons"), "objectAtIndex:", 1), "setKeyEquivalent:", nsString("\\e"));
      //}

      String doNotAsk = toStringViaUTF8(doNotAskText);
      if (!"-1".equals(doNotAsk)) {
        invoke(alert, "setShowsSuppressionButton:", 1);
        invoke(invoke(alert, "suppressionButton"), "setTitle:", doNotAskText);
        invoke(invoke(alert, "suppressionButton"), "setState:", "checked".equals(toStringViaUTF8(doNotAskChecked)));
      }

      invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
             createSelector("alertDidEnd:returnCode:contextInfo:"), focusedWindow);
    }
  };

  private static void processResult(Window w) {
    synchronized (lock) {
      if (!blockedDocumentRoots.keySet().contains(w)) {
        throw new RuntimeException("Window should be in th list.");
      }

      int openedSheetsForWindow = blockedDocumentRoots.get(w);

      if (openedSheetsForWindow < 1) {
        throw new RuntimeException("We should have at least one window in the list");
      }

      if (openedSheetsForWindow == 1) {
        // The last sheet
        blockedDocumentRoots.remove(w);
      } else {
        blockedDocumentRoots.put(w, openedSheetsForWindow - 1);
      }

    }
  }

  private MacMessagesImpl() {}

  private static Method  isModalBlockedMethod = null;
  private static Method  getModalBlockerMethod = null;

  static {
    if (SystemInfo.isMac) {
      final ID delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSAlertDelegate_");
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), SHEET_DID_END, "v*")) {
        throw new RuntimeException("Unable to add method to objective-c delegate class!");
      }
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showSheet:"), SIMPLE_SHEET_PANEL, "v*")) {
        throw new RuntimeException("Unable to add method to objective-c delegate class!");
      }
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showVariableButtonsSheet:"), VARIABLE_BUTTONS_SHEET_PANEL, "v*")) {
        throw new RuntimeException("Unable to add method to objective-c delegate class!");
      }
      Foundation.registerObjcClassPair(delegateClass);
    }

    Class [] noParams = new Class [] {};

    try {
      isModalBlockedMethod =  Window.class.getDeclaredMethod("isModalBlocked", noParams);
      getModalBlockerMethod =  Window.class.getDeclaredMethod("getModalBlocker", noParams);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }

  }

  @Override
  public void showOkMessageDialog(String title, String message, String okText, @Nullable Window window) {
    showMessageDialog(title, okText, null, null, message, window);
  }

  @Override
  public void showOkMessageDialog(String title, String message, String okText) {
    showMessageDialog(title, okText, null, null, message, null);
  }

  @Override
  public int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window) {
    return showMessageDialog(title, yesButton, null, noButton, message, window);
  }

  @Override
  public int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window,
                             @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption) {
    return showAlertDialog(title, yesButton, null, noButton, message, window, false, doNotAskDialogOption);
  }

  @Override
  public void showErrorDialog(String title, String message, String okButton, @Nullable Window window) {
    showAlertDialog(title, okButton, null, null, message, window, true, null);
  }

  @Override
  public int showYesNoCancelDialog(String title,
                                   String message,
                                   String defaultButton,
                                   String alternateButton,
                                   String otherButton,
                                   Window window,
                                   @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return showAlertDialog(title, defaultButton, alternateButton, otherButton, message, window, false, doNotAskOption);
  }




  final private static Object lock = new Object();

  final private static HashMap<Window, Integer> blockedDocumentRoots = new HashMap<Window, Integer>();

  final private static HashMap<Long, Window> windowFromId = new HashMap<Long, Window>();

  public static void pumpEventsDocumentExclusively (Window documentRoot) {

    Integer messageNumber = blockedDocumentRoots.get(documentRoot);

    EventQueue theQueue = documentRoot.getToolkit().getSystemEventQueue();

    AWTEvent event;
    do {
      try {
        event = theQueue.getNextEvent();
        boolean eventOk = true;
        if (event instanceof InputEvent) {
          final Object s = event.getSource();
          if (s instanceof Component) {
            Component c = (Component)s;

            Window w = findDocumentRoot(c);
            if (w == documentRoot) {
              eventOk = false;
              ((InputEvent)event).consume();
            }
          }
        }

        if (eventOk) {
          Class<?>[] paramString = new Class<?>[1];
          paramString[0] = AWTEvent.class;
          Method method = theQueue.getClass().getDeclaredMethod("dispatchEvent",paramString);
          method.setAccessible(true);
          method.invoke(theQueue, event);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    while (isBlockedDocumentRoot(documentRoot, messageNumber));
  }

  private static boolean isBlockedDocumentRoot(Window documentRoot, Integer messageNumber) {
    synchronized (lock) {
      return messageNumber.equals(blockedDocumentRoots.get(documentRoot));
    }
  }

  private static Window findDocumentRoot (final Component c) {
    if (c == null) return null;
    Window w = (c instanceof Window) ? (Window)c : SunToolkit.getContainingWindow(c);
    synchronized (c.getTreeLock()) {
      while (w.getOwner() != null) {
        w = w.getOwner();
      }
    }
    return w;
  }

  private static void startModal(final Window w, ID windowId) {
    synchronized (lock) {
      windowFromId.put(windowId.longValue(), w);
      if (blockedDocumentRoots.keySet().contains(w)) {
        blockedDocumentRoots.put(w, blockedDocumentRoots.get(w) + 1);
      } else {
        blockedDocumentRoots.put(w, 1);
      }
    }

    pumpEventsDocumentExclusively(w);
  }


  public int showMessageDialog(final String title, final String message, final String[] buttons, final boolean errorStyle,
                               @Nullable Window window, final int defaultOptionIndex,
                               final int focusedOptionIndex, @Nullable final DialogWrapper.DoNotAskOption doNotAskDialogOption) {


    Window foremostWindow = getForemostWindow(window);
    String foremostWindowTitle = getWindowTitle(foremostWindow);

    Window documentRoot = getDocumentRootFromWindow(foremostWindow);

    final ID nativeFocusedWindow = MacUtil.findWindowForTitle(foremostWindowTitle);

    if (foremostWindow != null) {

      final FocusTrackback[] focusTrackback = {new FocusTrackback(new Object(), documentRoot, true)};

      final ID delegate = invoke(Foundation.getObjcClass("NSAlertDelegate_"), "new");
      invoke(delegate, "autorelease");
      cfRetain(delegate);

      final ID buttonsArray = invoke("NSMutableArray", "array");
      for (String s : buttons) {
        ID s1 = nsString(UIUtil.removeMnemonic(s));
        invoke(buttonsArray, "addObject:", s1);
        cfRelease(s1);
      }

      final ID paramsArray = invoke("NSArray", "arrayWithObjects:", nsString(title),
                                    // replace % -> %% to avoid formatted parameters (causes SIGTERM)
                                    nsString(StringUtil.stripHtml(message == null ? "" : message, true).replace("%", "%%")),
                                    nativeFocusedWindow, nsString(""), nsString(errorStyle ? "error" : "-1"),
                                    nsString(doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()
                                             // TODO: state=!doNotAsk.shouldBeShown()
                                             ? "-1"
                                             : doNotAskDialogOption.getDoNotShowMessage()),
                                    nsString(Integer.toString(defaultOptionIndex)),
                                    nsString(Integer.toString(focusedOptionIndex)), buttonsArray,
                                    nsString(doNotAskDialogOption != null && !doNotAskDialogOption.isToBeShown() ? "checked" : "-1"), null);

      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false);

      runOrPostponeForWindow(documentRoot, new Runnable() {
        @Override
        public void run() {
          invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
                 createSelector("showVariableButtonsSheet:"), paramsArray, false);
        }
      });

      startModal(documentRoot, nativeFocusedWindow);

      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true);


      if (focusTrackback[0] != null &&
          !(focusTrackback[0].isSheduledForRestore() || focusTrackback[0].isWillBeSheduledForRestore())) {
        focusTrackback[0].setWillBeSheduledForRestore();

        IdeFocusManager mgr = IdeFocusManager.findInstanceByComponent(documentRoot);
        Runnable r = new Runnable() {
          public void run() {
            if (focusTrackback[0] != null) focusTrackback[0].restoreFocus();
            focusTrackback[0] = null;
          }
        };
        mgr.doWhenFocusSettlesDown(r);
      }
      return convertReturnCodeFromNativeMessageDialog(documentRoot);
    }
    return -1;
  }

  private static int convertReturnCodeFromNativeMessageDialog(Window documentRoot) {
    return resultsFromDocumentRoot.remove(documentRoot) - 1000;
  }

  private static String getWindowTitle(Window documentRoot) {
    String windowTitle;
    if (documentRoot instanceof Frame) {
      windowTitle = ((Frame)documentRoot).getTitle();
    } else if (documentRoot instanceof Dialog) {
      windowTitle = ((Dialog)documentRoot).getTitle();
    } else {
      throw new RuntimeException("The window is not a frame and not a dialog!");
    }
    return windowTitle;
  }

  public static int showAlertDialog(final String title,
                                    final String defaultText,
                                    @Nullable final String alternateText,
                                    @Nullable final String otherText,
                                    final String message,
                                    @Nullable Window window ,
                                    final boolean errorStyle,
                                    @Nullable final DialogWrapper.DoNotAskOption doNotAskDialogOption) {

    Window foremostWindow = getForemostWindow(window);
    String foremostWindowTitle = getWindowTitle(foremostWindow);

    Window documentRoot = getDocumentRootFromWindow(foremostWindow);

    final ID nativeFocusedWindow = MacUtil.findWindowForTitle(foremostWindowTitle);

    ID pool = invoke("NSAutoreleasePool", "new");
    try {

      final ID delegate = invoke(getObjcClass("NSAlertDelegate_"), "new");
      cfRetain(delegate);

      final ID paramsArray = invoke("NSArray", "arrayWithObjects:", nsString(title), nsString(UIUtil.removeMnemonic(defaultText)),
                                    nsString(otherText == null ? "-1" : UIUtil.removeMnemonic(otherText)),
                                    nsString(alternateText == null ? "-1" : UIUtil.removeMnemonic(alternateText)),
                                    // replace % -> %% to avoid formatted parameters (causes SIGTERM)
                                    nsString(StringUtil.stripHtml(message == null ? "" : message, true).replace("%", "%%")),
                                    nativeFocusedWindow, nsString(""), nsString(errorStyle ? "error" : "-1"),
                                    nsString(doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()
                                             // TODO: state=!doNotAsk.shouldBeShown()
                                             ? "-1"
                                             : doNotAskDialogOption.getDoNotShowMessage()),
                                    nsString(doNotAskDialogOption != null && !doNotAskDialogOption.isToBeShown() ? "checked" : "-1"), null);



      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false);

      runOrPostponeForWindow(documentRoot, new Runnable() {
        @Override
        public void run() {
          invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
                 createSelector("showSheet:"), paramsArray, false);
        }
      });
      startModal(documentRoot, nativeFocusedWindow);
      IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true);

    }
    finally {
      invoke(pool, "release");
    }
    return convertRetunCodeFromNativeAlertDialog(documentRoot, alternateText);

  }

  private static int convertRetunCodeFromNativeAlertDialog(Window documentRoot, String alternateText) {
    Integer result = resultsFromDocumentRoot.remove(documentRoot);

    // DEFAULT = 1
    // ALTERNATE = 0
    // OTHER = -1 (cancel)

    int cancelCode = 1;
    int code;
    if (alternateText != null) {
      // DEFAULT = 0
      // ALTERNATE = 1
      // CANCEL = 2

      cancelCode = 2;

      if (result == null) result = 2;

      switch (result) {
        case 1:
          code = 0;
          break;
        case 0:
          code = 1;
          break;
        case -1: // cancel
        default:
          code = 2;
          break;
      }
    }
    else {
      // DEFAULT = 0
      // CANCEL = 1

      cancelCode = 1;

      if (result == null) result = -1;

      switch (result) {
        case 1:
          code = 0;
          break;
        case -1: // cancel
        default:
          code = 1;
          break;
      }
    }
    return code;
  }

  private static void runOrPostponeForWindow(Window documentRoot, Runnable task) {
    synchronized (lock) {
      MacMessagesQueue<Runnable> queue = queuesFromDocumentRoot.get(documentRoot);

      if (queue == null) {
        queue = new MacMessagesQueue<Runnable>();
        queuesFromDocumentRoot.put(documentRoot, queue);
      }

      queue.runOrEnqueue(task);
    }
  }

  private static Window getForemostWindow(final Window window) {
    Window _window = null;

    Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
    if (focusOwner != null) {
      _window = SwingUtilities.getWindowAncestor(focusOwner);
    }

    if (_window == null && window != null) {
      focusOwner = window.getMostRecentFocusOwner();
      if (focusOwner != null) {
        _window = SwingUtilities.getWindowAncestor(focusOwner);
      }
    }

    if (_window == null) {
      focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (focusOwner != null) {
        _window = SwingUtilities.getWindowAncestor(focusOwner);
      }
    }

    if (_window == null) {
      _window = WindowManager.getInstance().findVisibleFrame();
    }

    if (_window != null) {
      synchronized (_window.getTreeLock()) {
        try {
          isModalBlockedMethod.setAccessible(true);
          if ((Boolean)isModalBlockedMethod.invoke(_window, null)) {

            getModalBlockerMethod.setAccessible(true);
            _window = (Dialog)getModalBlockerMethod.invoke(_window, null);
          }
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
        }
        catch (IllegalAccessException e) {
          LOG.error(e);
        }
      }
    }

    return _window;
  }

  /**
   * Document root is intended to queue messages per a document root
   */
  private static Window getDocumentRootFromWindow(Window window) {
    return findDocumentRoot(window);
  }

  public static int showMessageDialog(String title,
                                      String okText,
                                      @Nullable String alternateText,
                                      @Nullable String cancelText,
                                      String message,
                                      @Nullable Window window) {
    return showAlertDialog(title, okText, alternateText, cancelText, message, window, false, null);
  }
}
