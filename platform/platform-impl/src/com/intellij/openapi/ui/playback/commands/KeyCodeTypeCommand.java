// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Couple;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class KeyCodeTypeCommand extends AlphaNumericTypeCommand {

  public static final String PREFIX = CMD_PREFIX + "type";
  public static final String CODE_DELIMITER = ";";
  public static final String MODIFIER_DELIMITER = ":";

  public KeyCodeTypeCommand(String text, int line) {
    super(text, line);
  }

  @Override
  public Promise<Object> _execute(final PlaybackContext context) {
    String text = getText().substring(PREFIX.length()).trim();

    int textDelim = text.indexOf(" ");

    final String codes;
    if (textDelim >= 0) {
      codes = text.substring(0, textDelim);
    } else {
      codes = text;
    }

    final String unicode;
    if (codes.length() + 1 < text.length()) {
      unicode = text.substring(textDelim + 1);
    } else {
      unicode = "";
    }

    final ActionCallback result = new ActionCallback();
    inWriteSafeContext(() -> {
      TypingTarget typingTarget = findTarget(context);
      if (typingTarget != null) {
        typingTarget.type(unicode).doWhenDone(result.createSetDoneRunnable()).doWhenRejected(() -> typeCodes(context, context.getRobot(), codes).notify(result));
      } else {
        typeCodes(context, context.getRobot(), codes).notify(result);
      }
    });

    return Promises.toPromise(result);
  }

  private ActionCallback typeCodes(final PlaybackContext context, final Robot robot, final String codes) {
    final ActionCallback result = new ActionCallback();

    Runnable runnable = () -> {
      String[] pairs = codes.split(CODE_DELIMITER);
      for (String eachPair : pairs) {
        try {
          String[] splits = eachPair.split(MODIFIER_DELIMITER);
          Integer code = Integer.valueOf(splits[0]);
          Integer modifier = Integer.valueOf(splits[1]);
          //noinspection MagicConstant
          type(robot, code.intValue(), modifier.intValue());
        }
        catch (NumberFormatException e) {
          dumpError(context, "Invalid code: " + eachPair);
          result.setRejected();
          return;
        }
      }

      result.setDone();
    };


    if (SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    } else {
      runnable.run();
    }

    return result;
  }

  public static Couple<List<Integer>> parseKeyCodes(String keyCodesText) {
    List<Integer> codes = new ArrayList<>();
    List<Integer> modifiers = new ArrayList<>();

    if (keyCodesText != null) {
      String[] pairs = keyCodesText.split(CODE_DELIMITER);
      for (String each : pairs) {
        String[] strings = each.split(MODIFIER_DELIMITER);
        if (strings.length == 2) {
          codes.add(Integer.valueOf(strings[0]));
          modifiers.add(Integer.valueOf(strings[1]));
        }
      }
    }

    return Couple.of(codes, modifiers);
  }

  public static String unparseKeyCodes(Couple<List<Integer>> pairs) {
    StringBuilder result = new StringBuilder();

    List<Integer> codes = pairs.getFirst();
    List<Integer> modifiers = pairs.getSecond();

    for (int i = 0; i < codes.size(); i++) {
      Integer each = codes.get(i);
      result.append(each.toString());
      result.append(MODIFIER_DELIMITER);
      result.append(modifiers.get(i));
      if (i < codes.size() - 1) {
        result.append(CODE_DELIMITER);
      }
    }

    return result.toString();
  }

}
