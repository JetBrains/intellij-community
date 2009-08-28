package com.intellij.openapi.wm.ex;

import com.intellij.notification.impl.IdeNotificationArea;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

public interface StatusBarEx extends StatusBar {
  String getInfo();

  void clear();

  void addFileStatusComponent(StatusBarPatch component);

  void removeFileStatusComponent(StatusBarPatch component);

  void cleanupCustomComponents();

  void add(ProgressIndicatorEx indicator, TaskInfo info);

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody);

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener);

  void update(Editor editor);

  void dispose();

  IdeNotificationArea getNotificationArea();
}
