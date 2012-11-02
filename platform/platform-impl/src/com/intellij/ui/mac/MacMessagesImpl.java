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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author pegov
 */
public class MacMessagesImpl extends MacMessages {
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
          if (rootPane.getClientProperty(MacUtil.MAC_NATIVE_WINDOW_SHOWING) == Boolean.TRUE &&
              fakeDialogTitle.equals(rootPane.getClientProperty(MAC_SHEET_ID))) {
            processResult(rootPane, returnCode.intValue(), suppressState.intValue());
            break;
          }
        }
        else if (window instanceof JDialog) {
          JDialog dialog = (JDialog)window;
          JRootPane rootPane = dialog.getRootPane();
          if (rootPane.getClientProperty(MacUtil.MAC_NATIVE_WINDOW_SHOWING) == Boolean.TRUE &&
              fakeDialogTitle.equals(rootPane.getClientProperty(MAC_SHEET_ID))) {
            processResult(rootPane, returnCode.intValue(), suppressState.intValue());
          }
        }
      }

      cfRelease(self);
    }
  };

  private static final Callback VARIABLE_BUTTONS_SHEET_PANEL = new Callback() {
    public void callback(ID self, String selector, ID params) {
      ID title = invoke(params, "objectAtIndex:", 0);
      ID message = invoke(params, "objectAtIndex:", 1);
      ID focusedWindow = invoke(params, "objectAtIndex:", 2);
      ID fakeId = invoke(params, "objectAtIndex:", 3);
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
             createSelector("alertDidEnd:returnCode:contextInfo:"), fakeId);
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
      ID fakeId = invoke(params, "objectAtIndex:", 6);
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
      
      
      // it is impossible to override ESCAPE key behavior -> key should be named "Cancel" to be bound to ESC
      //if (!alternateExist) {
        //invoke(invoke(invoke(alert, "buttons"), "objectAtIndex:", 1), "setKeyEquivalent:", nsString("\\e"));
      //}
      
      String doNotAsk = toStringViaUTF8(doNotAskText);
      if (!"-1".equals(doNotAsk)) {
        invoke(alert, "setShowsSuppressionButton:", 1);
        invoke(invoke(alert, "suppressionButton"), "setTitle:", doNotAskText);
        invoke(invoke(alert, "suppressionButton"), "setState:", "checked".equals(toStringViaUTF8(doNotAskChecked)));
      }

      invoke(alert, "beginSheetModalForWindow:modalDelegate:didEndSelector:contextInfo:", focusedWindow, self,
             createSelector("alertDidEnd:returnCode:contextInfo:"), fakeId);
    }
  };

  private static void processResult(JRootPane rootPane, int returnCode, int suppressDialog) {
    rootPane.putClientProperty(MAC_SHEET_RESULT, returnCode);
    rootPane.putClientProperty(MAC_SHEET_SUPPRESS, suppressDialog == 1 ? Boolean.TRUE : Boolean.FALSE);
    rootPane.putClientProperty(MAC_SHEET_ID, null);
    rootPane.putClientProperty(MacUtil.MAC_NATIVE_WINDOW_SHOWING, null);
  }

  private static final String MAC_SHEET_RESULT = "mac_sheet_result";
  private static final String MAC_SHEET_SUPPRESS = "mac_sheet_suppress";
  private static final String MAC_SHEET_ID = "mac_sheet_id";

  private MacMessagesImpl() {
  }

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

  public int showMessageDialog(final String title, final String message, final String[] buttons, final boolean errorStyle,
                                @Nullable Window window, final int defaultOptionIndex,
                                final int focusedOptionIndex, @Nullable final DialogWrapper.DoNotAskOption doNotAskDialogOption) {
    return doForWindowAndTitle(new PairFunction<Pair<Window, String>, JRootPane, Integer>() {
      @Override
      public Integer fun(Pair<Window, String> windowAndTitle, JRootPane pane) {
        String _windowTitle = windowAndTitle.getSecond();
        Window _window = windowAndTitle.getFirst();

        final ID focusedWindow = MacUtil.findWindowForTitle(_windowTitle);
        if (focusedWindow != null) {
          String fakeTitle = null;

          final FocusTrackback[] focusTrackback = {new FocusTrackback(new Object(), _window, true)};

          ID pool = invoke("NSAutoreleasePool", "new");
          try {
            final ID delegate = invoke(Foundation.getObjcClass("NSAlertDelegate_"), "new");
            cfRetain(delegate);

            fakeTitle = String.format("MacSheetDialog-%d", delegate.intValue());

            final ID buttonsArray = invoke("NSMutableArray", "array");
            for (String s : buttons) {
              ID s1 = nsString(UIUtil.removeMnemonic(s));
              invoke(buttonsArray, "addObject:", s1);
              cfRelease(s1);
            }

            ID paramsArray = invoke("NSArray", "arrayWithObjects:", nsString(title),
                                    // replace % -> %% to avoid formatted parameters (causes SIGTERM)
                                    nsString(StringUtil.stripHtml(message == null ? "" : message, true).replace("%", "%%")),
                                    focusedWindow, nsString(fakeTitle), nsString(errorStyle ? "error" : "-1"),
                                    nsString(doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()
                                             // TODO: state=!doNotAsk.shouldBeShown()
                                             ? "-1"
                                             : doNotAskDialogOption.getDoNotShowMessage()), 
                                    nsString(Integer.toString(defaultOptionIndex)), 
                                    nsString(Integer.toString(focusedOptionIndex)), buttonsArray,
                                    nsString(doNotAskDialogOption != null && !doNotAskDialogOption.isToBeShown() ? "checked" : "-1"), null);

            IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false);

            invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
                   Foundation.createSelector("showVariableButtonsSheet:"), paramsArray, false);
          }
          finally {
            invoke(pool, "release");
          }

          if (fakeTitle != null) {
            pane.putClientProperty(MacUtil.MAC_NATIVE_WINDOW_SHOWING, Boolean.TRUE);
            pane.putClientProperty(MAC_SHEET_ID, fakeTitle);

            MacUtil.startModal(pane);
            
            IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true);
            
            Integer code = (Integer)pane.getClientProperty(MAC_SHEET_RESULT) - 1000; // see NSAlertFirstButtonReturn for more info
            boolean suppress = Boolean.TRUE == pane.getClientProperty(MAC_SHEET_SUPPRESS);

            final int cancelCode = buttons.length - 1;

            if (doNotAskDialogOption != null && doNotAskDialogOption.canBeHidden()) {
              if (cancelCode != code || doNotAskDialogOption.shouldSaveOptionsOnCancel()) {
                doNotAskDialogOption.setToBeShown(!suppress, code);
              }
            }

            pane.putClientProperty(MAC_SHEET_RESULT, null);
            pane.putClientProperty(MAC_SHEET_SUPPRESS, null);

            if (focusTrackback[0] != null &&
                !(focusTrackback[0].isSheduledForRestore() || focusTrackback[0].isWillBeSheduledForRestore())) {
              focusTrackback[0].setWillBeSheduledForRestore();

              IdeFocusManager mgr = IdeFocusManager.findInstanceByComponent(_window);
              Runnable r = new Runnable() {
                public void run() {
                  if (focusTrackback[0] != null) focusTrackback[0].restoreFocus();
                  focusTrackback[0] = null;
                }
              };
              mgr.doWhenFocusSettlesDown(r);
            }

            return code;
          }
        }

        return -1;
      }
    }, window);
  }

  private static int doForWindowAndTitle(PairFunction<Pair<Window, String>, JRootPane, Integer> fun, @Nullable Window window) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    JRootPane pane = null;
    String _windowTitle = null;

    Window _window = window == null ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() : window;
    if (_window == null) {
      Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
      if (focusOwner != null) {
        _window = SwingUtilities.getWindowAncestor(focusOwner);
      }
      
      if (_window == null) {
        _window = WindowManager.getInstance().findVisibleFrame();
      }
    }
    
    LOG.assertTrue(_window != null);
    
    if (!_window.isShowing()) {
      Container parent = _window.getParent();
      if (parent != null && parent instanceof Window) {
        _window = (Window)parent;
      }

      if (!_window.isShowing()) {
        Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
        if (focusOwner != null) {
          _window = SwingUtilities.getWindowAncestor(focusOwner);
        }
      }
    }

    LOG.assertTrue(_window.isShowing(), "Window MUST BE showing in screen!");

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
    
    if (_windowTitle == null) {
      _window = SwingUtilities.getWindowAncestor(_window);
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
    }

    LOG.assertTrue(_windowTitle != null && _windowTitle.length() > 0 && pane != null, "Window MUST have a title and a root pane!");

    return fun.fun(Pair.create(_window, _windowTitle), pane);
  }

  public static int showAlertDialog(final String title,
                                    final String defaultText,
                                    @Nullable final String alternateText,
                                    @Nullable final String otherText,
                                    final String message,
                                    @Nullable Window window,
                                    final boolean errorStyle,
                                    @Nullable final DialogWrapper.DoNotAskOption doNotAskDialogOption) {
    return doForWindowAndTitle(new PairFunction<Pair<Window, String>, JRootPane, Integer>() {
      @Override
      public Integer fun(Pair<Window, String> windowAndTitle, JRootPane pane) {
        String _windowTitle = windowAndTitle.getSecond();
        Window _window = windowAndTitle.getFirst();

        final ID focusedWindow = MacUtil.findWindowForTitle(_windowTitle);
        if (focusedWindow != null) {
          String fakeTitle = null;

          final FocusTrackback[] focusTrackback = {new FocusTrackback(new Object(), _window, true)};

          ID pool = invoke("NSAutoreleasePool", "new");
          try {
            final ID delegate = invoke(Foundation.getObjcClass("NSAlertDelegate_"), "new");
            cfRetain(delegate);

            fakeTitle = String.format("MacSheetDialog-%d", delegate.intValue());

            ID paramsArray = invoke("NSArray", "arrayWithObjects:", nsString(title), nsString(UIUtil.removeMnemonic(defaultText)),
                                    nsString(otherText == null ? "-1" : UIUtil.removeMnemonic(otherText)),
                                    nsString(alternateText == null ? "-1" : UIUtil.removeMnemonic(alternateText)),
                                    // replace % -> %% to avoid formatted parameters (causes SIGTERM)
                                    nsString(StringUtil.stripHtml(message == null ? "" : message, true).replace("%", "%%")),
                                    focusedWindow, nsString(fakeTitle), nsString(errorStyle ? "error" : "-1"),
                                    nsString(doNotAskDialogOption == null || !doNotAskDialogOption.canBeHidden()
                                             // TODO: state=!doNotAsk.shouldBeShown()
                                             ? "-1"
                                             : doNotAskDialogOption.getDoNotShowMessage()),
                                    nsString(doNotAskDialogOption != null && !doNotAskDialogOption.isToBeShown() ? "checked" : "-1"), null);

            IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(false);
            
            invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:",
                   Foundation.createSelector("showSheet:"), paramsArray, false);
          }
          finally {
            invoke(pool, "release");
          }

          if (fakeTitle != null) {
            pane.putClientProperty(MacUtil.MAC_NATIVE_WINDOW_SHOWING, Boolean.TRUE);
            pane.putClientProperty(MAC_SHEET_ID, fakeTitle);

            MacUtil.startModal(pane);
            
            IdeFocusManager.getGlobalInstance().setTypeaheadEnabled(true);
            
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

            if (doNotAskDialogOption != null && doNotAskDialogOption.canBeHidden()) {
              if (cancelCode != code || doNotAskDialogOption.shouldSaveOptionsOnCancel()) {
                doNotAskDialogOption.setToBeShown(!suppress, code);
              }
            }

            pane.putClientProperty(MAC_SHEET_RESULT, null);
            pane.putClientProperty(MAC_SHEET_SUPPRESS, null);

            if (focusTrackback[0] != null &&
                !(focusTrackback[0].isSheduledForRestore() || focusTrackback[0].isWillBeSheduledForRestore())) {
              focusTrackback[0].setWillBeSheduledForRestore();

              IdeFocusManager mgr = IdeFocusManager.findInstanceByComponent(_window);
              Runnable r = new Runnable() {
                public void run() {
                  if (focusTrackback[0] != null) focusTrackback[0].restoreFocus();
                  focusTrackback[0] = null;
                }
              };
              mgr.doWhenFocusSettlesDown(r);
            }

            return code;
          }
        }

        return -1;
      }
    }, window);
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
