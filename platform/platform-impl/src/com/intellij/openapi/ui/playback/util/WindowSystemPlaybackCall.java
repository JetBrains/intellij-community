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
package com.intellij.openapi.ui.playback.util;

import com.intellij.ide.UiActivityMonitor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.*;

public class WindowSystemPlaybackCall {

  public static AsyncResult<String> printFocus(final PlaybackContext context) {
    final AsyncResult result = new AsyncResult<String>();

    getUiReady(context).doWhenProcessed(() -> {
      final LinkedHashMap<String, String> focusInfo = getFocusInfo();
      if (focusInfo == null) {
        result.setRejected("No component focused");
        return;
      }

      StringBuffer text = new StringBuffer();
      for (Iterator<String> iterator = focusInfo.keySet().iterator(); iterator.hasNext(); ) {
        String key = iterator.next();
        text.append(key + "=" + focusInfo.get(key));
        if (iterator.hasNext()) {
          text.append("|");
        }
      }
      result.setDone(text.toString());
    });

    return result;
  }


  public static AsyncResult<String> waitForDialog(final PlaybackContext context, final String title) {
    final AsyncResult<String> result = new AsyncResult<>();

    final Ref<AWTEventListener> listener = new Ref<>();
    listener.set(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event.getID() == WindowEvent.WINDOW_ACTIVATED) {
          final Window wnd = ((WindowEvent)event).getWindow();
          if (wnd instanceof JDialog) {
            if (title.equals(((JDialog)wnd).getTitle())) {
              Toolkit.getDefaultToolkit().removeAWTEventListener(listener.get());
              SwingUtilities.invokeLater(() -> getUiReady(context).notify(result));
            }
          }
        }
      }
    });

    Toolkit.getDefaultToolkit().addAWTEventListener(listener.get(), WindowEvent.WINDOW_EVENT_MASK);

    SimpleTimer.getInstance().setUp(() -> {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener.get());
      if (!result.isProcessed()) {
        result.setRejected("Timed out waiting for window: " + title);
      }
    }, Registry.intValue("actionSystem.commandProcessingTimeout"));

    return result;
  }

  public static AsyncResult<String> checkFocus(final PlaybackContext context, String expected) {
    final AsyncResult<String> result = new AsyncResult<>();
    final Map<String, String> expectedMap = new LinkedHashMap<>();

    if (expected.length() > 0) {
      final String[] keyValue = expected.split("\\|");
      for (String each : keyValue) {
        final String[] eachPair = each.split("=");
        if (eachPair.length != 2) {
          result.setRejected("Syntax error, must be |-separated pairs key=value");
          return result;
        }

        expectedMap.put(eachPair[0], eachPair[1]);
      }
    }

    getUiReady(context).doWhenDone(() -> {
      try {
        doAssert(expectedMap, result, context);
      }
      catch (AssertionError error) {
        result.setRejected("Assertion failed: " + error.getMessage());
      }
    });

    return result;
  }

  public static AsyncResult<String> waitForToolWindow(final PlaybackContext context, final String id) {
    final AsyncResult<String> result = new AsyncResult<>();

    findProject().doWhenDone(new Consumer<Project>() {
      @Override
      public void consume(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(id);
        if (toolWindow == null) {
          result.setRejected("Cannot find tool window with id: " + id);
          return;
        }

        toolWindow.getReady(context).doWhenDone(result.createSetDoneRunnable()).doWhenRejected(() -> result.setRejected("Cannot activate tool window with id:" + id));
      }
    }).doWhenRejected(() -> result.setRejected("Cannot retrieve open project"));

    return result;
  }

  public static AsyncResult<Project> findProject() {
    final AsyncResult<Project> project = new AsyncResult<>();
    final IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    fm.doWhenFocusSettlesDown(() -> {
      Component parent = UIUtil.findUltimateParent(fm.getFocusOwner());
      if (parent instanceof IdeFrame) {
        IdeFrame frame = (IdeFrame)parent;
        if (frame.getProject() != null) {
          project.setDone(frame.getProject());
          return;
        }
      }

      project.setRejected();
    });

    return project;
  }

  public static AsyncResult<String> contextMenu(final PlaybackContext context, final String path) {
    final AsyncResult<String> result = new AsyncResult<>();

    final IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    fm.doWhenFocusSettlesDown(() -> {
      Component owner = fm.getFocusOwner();
      if (owner == null) {
        result.setRejected("No component focused");
        return;
      }

      ActionManager am = ActionManager.getInstance();
      AnAction showPopupMenu = am.getAction("ShowPopupMenu");
      if (showPopupMenu == null) {
        result.setRejected("Cannot find action: ShowPopupMenu");
        return;
      }

      am.tryToExecute(showPopupMenu, new MouseEvent(owner, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, true), null,
                      null, false).doWhenDone(
        () -> SwingUtilities.invokeLater(() -> {
          MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
          if (selectedPath.length == 0) {
            result.setRejected("Failed to find active popup menu");
            return;
          }
          selectNext(context, path.split("\\|"), 0, selectedPath[0].getSubElements(), result);
        })).doWhenRejected(() -> result.setRejected("Cannot invoke popup menu from the ShowPopupMenu action, action call rejected"));
    });

    return result;
  }

  private static void selectNext(final PlaybackContext context, final String[] toSelect, final int toSelectIndex, MenuElement[] menuElements, final AsyncResult<String> result) {
    if (menuElements == null || menuElements.length == 0) {
      result.setDone();
    }

    if (toSelectIndex > toSelect.length - 1) {
      result.setDone();
      return;
    }

    String target = toSelect[toSelectIndex];
    for (final MenuElement each : menuElements) {
      if (each.getComponent() instanceof AbstractButton) {
        final AbstractButton eachButton = (AbstractButton)each.getComponent();
        if (eachButton.getText() != null && eachButton.getText().startsWith(target)) {
          activateItem(context, each).doWhenDone(new Consumer<MenuElement[]>() {
            @Override
            public void consume(MenuElement[] menuElements) {
              selectNext(context, toSelect, toSelectIndex + 1, menuElements, result);
            }
          }).doWhenRejected(() -> {
            result.setRejected("Cannot activate menu element: " + eachButton.getText());
            return;
          });
          return;
        }
      }
      else {
        result.setRejected("Unknown class for context menu item: " + each.getComponent());
        return;
      }
    }

    result.setRejected("Failed to find menu item: " + target);
  }

  private static AsyncResult<MenuElement[]> activateItem(final PlaybackContext context, final MenuElement element) {
    final AsyncResult<MenuElement[]> result = new AsyncResult<>();
    final AbstractButton c = (AbstractButton)element.getComponent();

    final Runnable pressRunnable = () -> {
      Robot robot = context.getRobot();
      Point location = c.getLocationOnScreen();
      Dimension size = c.getSize();
      Point point = new Point(location.x + size.width / 2, location.y + size.height / 2);
      robot.mouseMove(point.x, point.y);
      robot.delay(90);
      robot.mousePress(InputEvent.BUTTON1_MASK);
      robot.delay(90);
      robot.mouseRelease(InputEvent.BUTTON1_MASK);
      robot.delay(90);
      context.flushAwtAndRunInEdt(() -> context.flushAwtAndRunInEdt(() -> {
        MenuElement[] subElements = element.getSubElements();
        if (subElements == null || subElements.length == 0) {
          result.setDone();
        }
        else {
          MenuElement[] menuElements = subElements[0].getSubElements();
          result.setDone(menuElements);
        }
      }));
    };

    if (c.isShowing()) {
      context.runPooledThread(pressRunnable);
    } else {
      context.delayAndRunInEdt(() -> {
        if (c.isShowing()) {
          context.runPooledThread(pressRunnable);
        } else {
          result.setRejected();
        }
      }, 1000);
    }

    return result;
  }

  public static ActionCallback getUiReady(final PlaybackContext context) {
    final ActionCallback result = new ActionCallback();
    context.flushAwtAndRunInEdt(() -> UiActivityMonitor.getInstance().getBusy().getReady(context).notify(result));
    return result;
  }

  private static void doAssert(Map<String, String> expected, AsyncResult<String> result, PlaybackContext context) throws AssertionError {
    final LinkedHashMap<String, String> actual = getFocusInfo();

    if (actual == null) {
      result.setRejected("No component focused");
      return;
    }

    Set testedKeys = new LinkedHashSet<String>();
    for (String eachKey : expected.keySet()) {
      testedKeys.add(eachKey);

      final String actualValue = actual.get(eachKey);
      final String expectedValue = expected.get(eachKey);

      if (!expectedValue.equals(actualValue)) {
        result.setRejected(eachKey + " expected: " + expectedValue + " but was: " + actualValue);
        return;
      }
    }

    Map<String, String> untested = new HashMap<>();
    for (String eachKey : actual.keySet()) {
      if (testedKeys.contains(eachKey)) continue;
      untested.put(eachKey, actual.get(eachKey));
    }

    StringBuffer untestedText = new StringBuffer();
    for (String each : untested.keySet()) {
      if (untestedText.length() > 0) {
        untestedText.append(",");
      }
      untestedText.append(each).append("=").append(untested.get(each));
    }

    result.setDone();

    if (untestedText.length() > 0) {
      context.message("Untested focus info: " + untestedText.toString(), context.getCurrentLine());
    }
  }

  private static LinkedHashMap<String, String> getFocusInfo() {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    if (owner == null) {
      return null;
    }

    Component eachParent = owner;
    final LinkedHashMap<String, String> actual = new LinkedHashMap<>();
    while (eachParent != null) {
      if (eachParent instanceof Queryable) {
        ((Queryable)eachParent).putInfo(actual);
      }

      eachParent = eachParent.getParent();
    }
    return actual;
  }

  public static AsyncResult<String> flushUi(PlaybackContext context) {
    AsyncResult<String> result = new AsyncResult<>();
    getUiReady(context).notify(result);
    return result;
  }
}
