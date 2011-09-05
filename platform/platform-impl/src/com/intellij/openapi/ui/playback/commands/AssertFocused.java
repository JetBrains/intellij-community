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
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;

import java.awt.*;
import java.util.*;

public class AssertFocused extends AbstractCommand {

  public static String PREFIX = CMD_PREFIX + "assert focused";

  public AssertFocused(String text, int line) {
    super(text, line);
  }

  protected ActionCallback _execute(final PlaybackContext context) {
    final ActionCallback result = new ActionCallback();

    String text = getText().substring(PREFIX.length()).trim();
    final Map<String, String> expected = new LinkedHashMap<String, String>();

    if (text.length() > 0) {
      final String[] keyValue = text.split(",");
      for (String each : keyValue) {
        final String[] eachPair = each.split("=");
        if (eachPair.length != 2) {
          context.error("Syntax error, must be comma-separated pairs key=value", getLine());
          result.setRejected();
          return result;
        }

        expected.put(eachPair[0], eachPair[1]);
      }
    }

    IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
      public void run() {
        try {
          doAssert(expected, context);
          result.setDone();
        }
        catch (AssertionError error) {
          context.error("Assertion failed: " + error.getMessage(), getLine());
          result.setRejected();
        }
      }
    });

    return result;
  }

  private void doAssert(Map<String, String> expected, PlaybackContext context) throws AssertionError {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    if (owner == null) {
      throw new AssertionError("No component focused");
    }

    Component eachParent = owner;
    final LinkedHashMap<String, String> actual = new LinkedHashMap<String, String>();
    while (eachParent != null) {
      if (eachParent instanceof Queryable) {
        ((Queryable)eachParent).putInfo(actual);
      }

      eachParent = eachParent.getParent();
    }

    Set testedKeys = new LinkedHashSet<String>();
    for (String eachKey : expected.keySet()) {
      testedKeys.add(eachKey);

      final String actualValue = actual.get(eachKey);
      final String expectedValue = expected.get(eachKey);

      if (!expectedValue.equals(actualValue)) {
        throw new AssertionError(eachKey + " expected: " + expectedValue + " but was: " + actualValue);
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

    context.message("Untested info: " + untestedText.toString(), getLine());
  }

}