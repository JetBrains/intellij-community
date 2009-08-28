package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;

import javax.swing.*;
import java.util.Date;
import java.awt.*;

/**
 * @author spleaner
 */
public interface Notification {
  @NotNull
  String getId();

  @NotNull
  String getName();

  @NotNull
  String getDescription();

  @NotNull
  NotificationListener getListener();

  @NotNull
  NotificationType getType();

  Date getDate();

  @NotNull
  Icon getIcon();

  @NotNull
  Color getBackgroundColor();
}
