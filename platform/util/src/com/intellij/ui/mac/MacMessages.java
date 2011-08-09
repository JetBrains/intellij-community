/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author pegov
 */
public class MacMessages {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.MacMessages");

  private static final Callback SHEET_DID_END = new Callback() {
    public void callback(ID self, String selector, ID alert, ID returnCode, ID contextInfo) {
      String fakeDialogTitle = toStringViaUTF8(contextInfo);
      Window[] windows = Window.getWindows();

      ID suppressState = invoke(invoke(alert, "suppressionButton"), "state");
      
      for (Window window : windows) {
        if (window instanceof JFrame) {
          JFrame frame = (JFrame)window;
          JRootPane rootPane = frame.getRootPane();
          if (rootPane.getClientProperty(MAC_SHEET_ACTIVE) == Boolean.TRUE &&
              fakeDialogTitle.equals(rootPane.getClientProperty(MAC_SHEET_ID))) {
            processResult(rootPane, returnCode.intValue(), suppressState.intValue());
            break;
          }
        }
        else if (window instanceof JDialog) {
          JDialog dialog = (JDialog)window;
          JRootPane rootPane = dialog.getRootPane();
          if (rootPane.getClientProperty(MAC_SHEET_ACTIVE) == Boolean.TRUE &&
              fakeDialogTitle.equals(rootPane.getClientProperty(MAC_SHEET_ID))) {
            processResult(rootPane, returnCode.intValue(), suppressState.intValue());
          }
        }
      }

      cfRelease(self);
    }
  };

  private static final Callback MAIN_THREAD_RUNNABLE = new Callback() {
    public void callback(ID self, String selector, ID params) {
      ID title = invoke(params, "objectAtIndex:", 0);
      ID defaultText = invoke(params, "objectAtIndex:", 1);
      ID otherText = invoke(params, "objectAtIndex:", 2);
      ID alternateText = invoke(params, "objectAtIndex:", 3);
      ID message = invoke(params, "objectAtIndex:", 4);
      ID focusedWindow = invoke(params, "objectAtIndex:", 5);
      ID fakeId = invoke(params, "objectAtIndex:", 6);
      ID alertStyle = invoke(params, "objectAtIndex:", 7);
      ID doNotAskText = invoke(params, "objectAtIndex:", 8);

      boolean alternateExist = !"-1".equals(toStringViaUTF8(alternateText));
      boolean otherExist = !"-1".equals(toStringViaUTF8(otherText));

      final ID alert = invoke("NSAlert", "alertWithMessageText:defaultButton:alternateButton:otherButton:informativeTextWithFormat:",
                              title, defaultText, alternateExist ? alternateText : null, otherExist ? otherText : null, message);

      if ("error".equals(toStringViaUTF8(alertStyle))) {
        invoke(alert, "setAlertStyle:", 2); // NSCriticalAlertStyle = 2
      }

      String doNotAsk = toStringViaUTF8(doNotAskText);
      if (!"-1".equals(doNotAsk)) {
        invoke(alert, "setShowsSuppressionButton:", 1);
        invoke(invoke(alert, "suppressionButton"), "setTitle:", doNotAskText);
      }

      invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
             createSelector("alertDidEnd:returnCode:contextInfo:"), fakeId);
    }
  };

  private static void processResult(JRootPane rootPane, int returnCode, int suppressDialog) {
    rootPane.putClientProperty(MAC_SHEET_RESULT, returnCode);
    rootPane.putClientProperty(MAC_SHEET_SUPPRESS, suppressDialog == 1 ? Boolean.TRUE : Boolean.FALSE);
    rootPane.putClientProperty(MAC_SHEET_ID, null);
    rootPane.putClientProperty(MAC_SHEET_ACTIVE, null);
  }

  private static final String MAC_SHEET_ACTIVE = "mac_sheet_active";
  private static final String MAC_SHEET_RESULT = "mac_sheet_result";
  private static final String MAC_SHEET_SUPPRESS = "mac_sheet_suppress";
  private static final String MAC_SHEET_ID = "mac_sheet_id";

  private MacMessages() {
  }

  static {
    final ID delegateClass = Foundation.registerObjcClass(Foundation.getClass("NSObject"), "NSAlertDelegate_");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("alertDidEnd:returnCode:contextInfo:"), SHEET_DID_END, "v*")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("showSheet:"), MAIN_THREAD_RUNNABLE, "v*")) {
      throw new RuntimeException("Unable to add method to objective-c delegate class!");
    }

    Foundation.registerObjcClassPair(delegateClass);
  }

  public static void showOkMessageDialog(String title, String message, String okText, @Nullable Window window) {
    showMessageDialog(title, okText, null, null, message, window);
  }

  public static void showOkMessageDialog(String title, String message, String okText) {
    showMessageDialog(title, okText, null, null, message, null);
  }

  public static int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window) {
    return showMessageDialog(title, yesButton, null, noButton, message, window);
  }

  public static int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window,
                                    @Nullable DoNotAskDialogOption doNotAskDialogOption) {
    return showAlertDialog(title, yesButton, null, noButton, message, window, false, doNotAskDialogOption);
  }

  public static void showErrorDialog(String title, String message, String okButton, @Nullable Window window) {
    showAlertDialog(title, okButton, null, null, message, window, true, null);
  }

  public static int showYesNoCancelDialog(String title,
                                          String message,
                                          String defaultButton,
                                          String alternateButton,
                                          String otherButton,
                                          Window window,
                                          @Nullable DoNotAskDialogOption doNotAskOption) {
    return showAlertDialog(title, defaultButton, alternateButton, otherButton, message, window, false, doNotAskOption);
  }

  public static int showAlertDialog(String title,
                                    String defaultText,
                                    @Nullable String alternateText,
                                    @Nullable String otherText,
                                    String message,
                                    @Nullable Window window,
                                    boolean errorStyle,
                                    @Nullable DoNotAskDialogOption doNotAskDialogOption) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    JRootPane pane = null;
    String _windowTitle = null;

    final Window _window = window == null ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() : window;
    if (_window instanceof JFrame) {
      JFrame frame = (JFrame)_window;
      pane = frame.getRootPane();
      _windowTitle = frame.getTitle();
    }
    else if (_window instanceof JDialog) {
      JDialog dialog = (JDialog)_window;
      pane = dialog.getRootPane();
      _windowTitle = dialog.getTitle();
    }

    LOG.assertTrue(_windowTitle != null && _windowTitle.length() > 0 && pane != null, "Window should have a title and a root pane!");

    final ID focusedWindow = MacUtil.findWindowForTitle(_windowTitle);
    if (focusedWindow != null) {
      String fakeTitle = null;

      ID pool = invoke("NSAutoreleasePool", "new");
      try {
        final ID delegate = invoke(Foundation.getClass("NSAlertDelegate_"), "new");
        cfRetain(delegate);

        fakeTitle = String.format("MacSheetDialog-%d", delegate.intValue());

        ID paramsArray = invoke("NSArray", "arrayWithObjects:", cfString(title), cfString(UIUtil.removeMnemonic(defaultText)),
                                cfString(otherText == null ? "-1" : UIUtil.removeMnemonic(otherText)),
                                cfString(alternateText == null ? "-1" : UIUtil.removeMnemonic(alternateText)), cfString(StringUtil.stripHtml(message, true)),
                                focusedWindow, cfString(fakeTitle), cfString(errorStyle ? "error" : "-1"),
                                cfString(doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()
                                         // TODO: state=!doNotAsk.shouldBeShown()
                                         ? "-1"
                                         : doNotAskDialogOption.getDoNotShowMessage()), null);

        invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
               Foundation.createSelector("showSheet:"), paramsArray, true);
      }
      finally {
        invoke(pool, "release");
      }

      if (fakeTitle != null) {
        pane.putClientProperty(MAC_SHEET_ACTIVE, Boolean.TRUE);
        pane.putClientProperty(MAC_SHEET_ID, fakeTitle);

        startModal(pane);
        Integer result = (Integer)pane.getClientProperty(MAC_SHEET_RESULT);
        boolean suppress = Boolean.TRUE == pane.getClientProperty(MAC_SHEET_SUPPRESS);
        
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
        } else {
          // DEFAULT = 0
          // CANCEL = 1
          
          cancelCode = 1;
    
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

        if (doNotAskDialogOption != null && doNotAskDialogOption.canBeHidden()) {
          if (cancelCode != code || doNotAskDialogOption.shouldSaveOptionsOnCancel()) {
            doNotAskDialogOption.setToBeShown(!suppress, code);
          }
        }
        
        pane.putClientProperty(MAC_SHEET_RESULT, null);
        pane.putClientProperty(MAC_SHEET_SUPPRESS, null);
        
        return code;
      }
    }

    return -1;
  }

  public static int showMessageDialog(String title,
                                      String okText,
                                      @Nullable String alternateText,
                                      @Nullable String cancelText,
                                      String message,
                                      @Nullable Window window) {
    return showAlertDialog(title, okText, alternateText, cancelText, message, window, false, null);
  }

  private static synchronized void startModal(JRootPane pane) {
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        EventQueue theQueue = pane.getToolkit().getSystemEventQueue();

        while (pane.getClientProperty(MAC_SHEET_ACTIVE) == Boolean.TRUE) {
          AWTEvent event = theQueue.getNextEvent();
          Object source = event.getSource();
          if (event instanceof ActiveEvent) {
            ((ActiveEvent)event).dispatch();
          }
          else if (source instanceof Component) {
            ((Component)source).dispatchEvent(event);
          }
          else if (source instanceof MenuComponent) {
            ((MenuComponent)source).dispatchEvent(event);
          }
          else {
            System.err.println("Unable to dispatch: " + event);
          }
        }
      }
      else {
        while (pane.getClientProperty(MAC_SHEET_ACTIVE) == Boolean.TRUE) {
          // TODO:
          //wait();
        }
      }
    }
    catch (InterruptedException ignored) {
    }
  }
}