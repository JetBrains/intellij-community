// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.util.*;

public class AssertFocused extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "assert focused";

  public AssertFocused(String text, int line) {
    super(text, line);
  }

  protected Promise<Object> _execute(final PlaybackContext context) {
    String text = getText().substring(PREFIX.length()).trim();
    final Map<String, String> expected = new LinkedHashMap<>();

    if (text.length() > 0) {
      final String[] keyValue = text.split(",");
      for (String each : keyValue) {
        final String[] eachPair = each.split("=");
        if (eachPair.length != 2) {
          String error = "Syntax error, must be comma-separated pairs key=value";
          context.error(error, getLine());
          return Promises.rejectedPromise(error);
        }

        expected.put(eachPair[0], eachPair[1]);
      }
    }

    final AsyncPromise<Object> result = new AsyncPromise<>();
    IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> {
      try {
        doAssert(expected, context);
        result.setResult(null);
      }
      catch (AssertionError error) {
        context.error("Assertion failed: " + error.getMessage(), getLine());
        result.setError(error);
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
    final LinkedHashMap<String, String> actual = new LinkedHashMap<>();
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

    context.message("Untested info: " + untestedText.toString(), getLine());
  }

}