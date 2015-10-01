/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.notification.impl.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * @author spleaner
 */
public class NotificationsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.ui.NotificationsUtil");
  private static final int TITLE_LIMIT = 1000;
  private static final int CONTENT_LIMIT = 10000;

  public static String buildHtml(@NotNull final Notification notification, @Nullable String style) {
    String title = notification.getTitle();
    String content = notification.getContent();
    if (title.length() > TITLE_LIMIT || content.length() > CONTENT_LIMIT) {
      LOG.info("Too large notification " + notification + " of " + notification.getClass() +
               "\nListener=" + notification.getListener() +
               "\nTitle=" + title +
               "\nContent=" + content);
      title = StringUtil.trimLog(title, TITLE_LIMIT);
      content = StringUtil.trimLog(content, CONTENT_LIMIT);
    }
    return buildHtml(title, content, style, "#" + ColorUtil.toHex(getMessageType(notification).getTitleForeground()));
  }

  public static String buildHtml(@NotNull final String title, @NotNull final String content, @Nullable String style) {
    return buildHtml(title, content, style, null);
  }

  @NotNull
  private static String buildHtml(@NotNull String title, @NotNull String content, @Nullable String style, @Nullable String color) {
    String result = "";
    if (style != null) {
      result += "<div style=\"" + style + "\">";
    }
    result += "<b" + (color == null ? ">" : " color=\"" + color + "\">") + title + "</b><p>" + content + "</p>";
    if (style != null) {
      result += "</div>";
    }
    return XmlStringUtil.wrapInHtml(result);
  }

  @Nullable
  public static HyperlinkListener wrapListener(@NotNull final Notification notification) {
    final NotificationListener listener = notification.getListener();
    if (listener == null) return null;

    return new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final NotificationListener listener1 = notification.getListener();
          if (listener1 != null) {
            listener1.hyperlinkUpdate(notification, e);
          }
        }
      }
    };
  }

  public static Icon getIcon(@NotNull final Notification notification) {
    Icon icon = notification.getIcon();
    return icon != null ? icon : getMessageType(notification).getDefaultIcon();
  }

  public static MessageType getMessageType(@NotNull Notification notification) {
    switch (notification.getType()) {
      case WARNING: return MessageType.WARNING;
      case ERROR: return MessageType.ERROR;
      case INFORMATION:
      default: return MessageType.INFO;
    }
  }

  public static Color getBackground(@NotNull final Notification notification) {
    return getMessageType(notification).getPopupBackground();
  }

  public static Color getBorderColor(Notification notification) {
    switch (notification.getType()) {
      case ERROR:
        return new JBColor(Color.gray, new Color(0xc8c8c8));
      case WARNING:
        return new JBColor(Color.gray, new Color(0x615f51));
      case INFORMATION:
      default:
        return new JBColor(Color.gray, new Color(0x205c00));
    }
  }
}
