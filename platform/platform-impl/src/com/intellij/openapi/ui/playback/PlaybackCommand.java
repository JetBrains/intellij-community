package com.intellij.openapi.ui.playback;

import com.intellij.openapi.util.ActionCallback;

import java.awt.*;

public interface PlaybackCommand {
  ActionCallback execute(PlaybackRunner.StatusCallback cb, Robot robot);
  boolean canGoFurther();
}