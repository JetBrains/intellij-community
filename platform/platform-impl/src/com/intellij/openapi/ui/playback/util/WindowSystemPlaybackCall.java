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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.util.*;

public class WindowSystemPlaybackCall {

  public static AsyncResult<String> printFocus(final PlaybackContext context) {
    final AsyncResult result = new AsyncResult<String>();

    getUiReady(context).doWhenProcessed(new Runnable() {
      @Override
      public void run() {
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
      }
    });

    return result;
  }

  public static AsyncResult<String> checkFocus(final PlaybackContext context, String expected) {
    final AsyncResult<String> result = new AsyncResult<String>();
    final Map<String, String> expectedMap = new LinkedHashMap<String, String>();

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

    getUiReady(context).doWhenDone(new Runnable() {
      @Override
      public void run() {
        try {
          doAssert(expectedMap, result, context);
        }
        catch (AssertionError error) {
          result.setRejected("Assertion failed: " + error.getMessage());
        }
      }
    });

    return result;
  }

  public static AsyncResult<String> waitForToolWindow(final PlaybackContext context, final String id) {
    final AsyncResult<String> result = new AsyncResult<String>();

    findProject().doWhenDone(new AsyncResult.Handler<Project>() {
      @Override
      public void run(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(id);
        if (toolWindow == null) {
          result.setRejected("Cannot find tool window with id: " + id);
          return;
        }

        toolWindow.getReady(context).doWhenDone(new Runnable() {
          @Override
          public void run() {
            result.setDone();
          }
        }).doWhenRejected(new Runnable() {
          @Override
          public void run() {
            result.setRejected("Cannot activate tool window with id:" + id);
          }
        });
      }
    }).doWhenRejected(new Runnable() {
      @Override
      public void run() {
        result.setRejected("Cannot retrieve open project");
      }
    });

    return result;
  }

  private static AsyncResult<Project> findProject() {
    final AsyncResult<Project> project = new AsyncResult<Project>();
    final IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    fm.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        Component parent = UIUtil.findUltimateParent(fm.getFocusOwner());
        if (parent instanceof IdeFrame) {
          IdeFrame frame = (IdeFrame)parent;
          if (frame.getProject() != null) {
            project.setDone(frame.getProject());
            return;
          }
        }

        project.setRejected();
      }
    });

    return project;
  }

  private static ActionCallback getUiReady(final PlaybackContext context) {
    final ActionCallback result = new ActionCallback();
    context.flushAwtAndRun(new Runnable() {
      @Override
      public void run() {
        UiActivityMonitor.getInstance().getBusy().getReady(context).notify(result);
      }
    });
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

    Map<String, String> untested = new HashMap<String, String>();
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
    final LinkedHashMap<String, String> actual = new LinkedHashMap<String, String>();
    while (eachParent != null) {
      if (eachParent instanceof Queryable) {
        ((Queryable)eachParent).putInfo(actual);
      }

      eachParent = eachParent.getParent();
    }
    return actual;
  }

  public static AsyncResult<String> flushUi(PlaybackContext context) {
    AsyncResult<String> result = new AsyncResult<String>();
    getUiReady(context).notify(result);
    return result;
  }
}
