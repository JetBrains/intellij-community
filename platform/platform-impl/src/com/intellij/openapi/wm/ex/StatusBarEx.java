// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.Tooltip;

public interface StatusBarEx extends StatusBar {
  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull @PopupContent String htmlBody);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull @PopupContent String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener);

  void addProgress(@NotNull ProgressIndicatorEx indicator, @NotNull TaskInfo info);

  List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses();

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);

  Dimension getSize();

  boolean isVisible();
}
