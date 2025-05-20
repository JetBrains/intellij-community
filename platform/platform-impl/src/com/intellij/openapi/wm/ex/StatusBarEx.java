// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressModel;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

public interface StatusBarEx extends StatusBar {
  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull @PopupContent String htmlBody);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull @PopupContent String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener);

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * See {@link ProgressIndicator} notice.
   * </p>
   */
  @ApiStatus.Obsolete
  void addProgress(@NotNull ProgressIndicatorEx indicator, @NotNull TaskInfo info);


  /**
   * @deprecated Progresses in the StatusBar now use the {@link ProgressModel} API instead of {@link ProgressIndicator}.
   * See {@link ProgressIndicator} notice.
   * <p>
   * Please use {@link #getBackgroundProcessModels()} instead.
   * </p>
   */
  @Deprecated(since = "2025.2", forRemoval = true)
  default @Unmodifiable List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses() {
    return ContainerUtil.map(getBackgroundProcessModels(), pair -> new Pair<>(pair.getFirst(), pair.getSecond().getProgressIndicator()));
  }

  List<Pair<TaskInfo, ProgressModel>> getBackgroundProcessModels();

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);

  Dimension getSize();

  boolean isVisible();
}
