package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class DelayCommand extends AbstractCommand {
  public static String PREFIX = CMD_PREFIX + "delay";

  public DelayCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String s = getText().substring(PREFIX.length()).trim();

    try {
      final Integer delay = Integer.valueOf(s);
      robot.delay(delay.intValue());
    }
    catch (NumberFormatException e) {
      dumpError(cb, "Invalid delay value: " + s);
      return new ActionCallback.Rejected();
    }

    return new ActionCallback.Done();
  }
}