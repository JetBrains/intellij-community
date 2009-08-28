package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class KeyShortcutCommand extends TypeCommand {

  public static String PREFIX = CMD_PREFIX + "[";
  public static String POSTFIX = CMD_PREFIX + "]";

  public KeyShortcutCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String one = getText().substring(PREFIX.length());
    if (!one.endsWith("]")) {
      dumpError(cb, "Expected " + "]");
      return new ActionCallback.Rejected();
    }

    type(robot, getFromShortcut(one.substring(0, one.length() - 1).trim()));

    return new ActionCallback.Done();
  }
}