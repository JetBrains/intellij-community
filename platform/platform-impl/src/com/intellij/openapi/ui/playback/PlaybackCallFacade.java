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
package com.intellij.openapi.ui.playback;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jdom.JDOMException;

import java.awt.*;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/3/11
 * Time: 4:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlaybackCallFacade {

  public static AsyncResult<String> openProject(final PlaybackContext context, String path) {
    final AsyncResult<String> result = new AsyncResult<String>();
    final ProjectManager pm = ProjectManager.getInstance();
    final Ref<ProjectManagerListener> listener = new Ref<ProjectManagerListener>();
    listener.set(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(final Project project) {
        StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
          @Override
          public void run() {
            pm.removeProjectManagerListener(listener.get());
            result.setDone("opened successfully: " + project.getProjectFilePath());
          }
        });
      }
    });
    pm.addProjectManagerListener(listener.get());

    try {
      pm.loadAndOpenProject(path);
    }
    catch (Exception e) {
      context.getCallback().error(e.getMessage(), context.getCurrentLine());
      result.setRejected();
    }

    return result;
  }

  public static AsyncResult<String> printFocus(final PlaybackContext context) {
    final AsyncResult result = new AsyncResult<String>();

    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
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
      final String[] keyValue = expected.split("|");
      for (String each : keyValue) {
        final String[] eachPair = each.split("=");
        if (eachPair.length != 2) {
          result.setRejected("Syntax error, must be |-separated pairs key=value");
          return result;
        }

        expectedMap.put(eachPair[0], eachPair[1]);
      }
    }

    IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
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

    context.getCallback().message("Untested info: " + untestedText.toString(), context.getCurrentLine());
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
}
