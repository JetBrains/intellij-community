// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * @author spleaner
 */
public final class NotificationsUtil {
  private static final Logger LOG = Logger.getInstance(NotificationsUtil.class);
  private static final int TITLE_LIMIT = 1000;
  private static final int CONTENT_LIMIT = 10000;

  private static final @NlsSafe String P_TAG = "<p/>";
  private static final @NlsSafe String BR_TAG = "<br>";

  @NotNull
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
    return buildHtml(title, null, content, style, "#" + ColorUtil.toHex(getMessageType(notification).getTitleForeground()), null, null);
  }

  @NotNull
  @Nls
  public static String buildHtml(@NotNull final Notification notification,
                                 @Nullable String style,
                                 boolean isContent,
                                 @Nullable Color color,
                                 @Nullable String contentStyle) {
    String title = !isContent ? notification.getTitle() : "";
    String subtitle = !isContent ? notification.getSubtitle() : null;
    String content = isContent ? notification.getContent() : "";
    if (title.length() > TITLE_LIMIT || StringUtil.length(subtitle) > TITLE_LIMIT || content.length() > CONTENT_LIMIT) {
      LOG.info("Too large notification " + notification + " of " + notification.getClass() +
               "\nListener=" + notification.getListener() +
               "\nTitle=" + title +
               "\nSubtitle=" + subtitle +
               "\nContent=" + content);
      title = StringUtil.trimLog(title, TITLE_LIMIT);
      subtitle = StringUtil.trimLog(StringUtil.notNullize(subtitle), TITLE_LIMIT);
      content = StringUtil.trimLog(content, CONTENT_LIMIT);
    }
    if (isContent) {
      content = StringUtil.replace(content, P_TAG, BR_TAG);
    }
    String colorText = color == null ? null : "#" + ColorUtil.toHex(color);
    return buildHtml(title, subtitle, content, style, isContent ? null : colorText, isContent ? colorText : null, contentStyle);
  }

  @NotNull
  @Nls
  public static String buildHtml(@Nullable @Nls String title,
                                 @Nullable @Nls String subtitle,
                                 @Nullable @Nls String content,
                                 @Nullable String style,
                                 @Nullable String titleColor,
                                 @Nullable String contentColor,
                                 @Nullable String contentStyle) {
    if (StringUtil.isEmpty(title) && !StringUtil.isEmpty(subtitle)) {
      title = subtitle;
      subtitle = null;
    }
    else if (!StringUtil.isEmpty(title) && !StringUtil.isEmpty(subtitle)) {
      title += ":";
    }

    HtmlBuilder htmlBuilder = new HtmlBuilder();
    if (StringUtil.isNotEmpty(title)) {
      HtmlChunk.Element titleChunk = HtmlChunk.raw(title).bold();
      if (StringUtil.isNotEmpty(titleColor)) {
        titleChunk = titleChunk.attr("color", titleColor);
      }

      htmlBuilder.append(titleChunk);
    }

    if (StringUtil.isNotEmpty(subtitle)) {
      htmlBuilder.nbsp().append(StringUtil.isNotEmpty(titleColor) ?
                                HtmlChunk.span().attr("color", titleColor).addText(subtitle) :
                                HtmlChunk.raw(subtitle));
    }

    if (StringUtil.isNotEmpty(content)) {
      HtmlChunk.Element contentChunk = HtmlChunk.raw(content).wrapWith(HtmlChunk.div());
      if (StringUtil.isNotEmpty(contentStyle)) {
        contentChunk = contentChunk.style(contentStyle);
      }

      if (StringUtil.isNotEmpty(contentColor)) {
        contentChunk = contentChunk.attr("color", contentColor);
      }

      htmlBuilder.append(contentChunk);
    }

    return StringUtil.isNotEmpty(style) ?
           htmlBuilder.wrapWith(HtmlChunk.div(style)).wrapWith(HtmlChunk.html()).toString() :
           htmlBuilder.wrapWithHtmlBody().toString();
  }

  @Nullable
  public static String getFontStyle() {
    String fontName = getFontName();
    return StringUtil.isEmpty(fontName) ? null : "font-family:" + fontName + ";";
  }

  @Nullable
  public static Integer getFontSize() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideLafFonts()) {
      return uiSettings.getFontSize();
    }
    Font font = UIUtil.getLabelFont();
    return font == null ? null : font.getSize();
  }

  @Nullable
  public static String getFontName() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideLafFonts()) {
      return uiSettings.getFontFace();
    }
    Font font = UIUtil.getLabelFont();
    return font == null ? null : font.getName();
  }

  @Nullable
  public static HyperlinkListener wrapListener(@NotNull final Notification notification) {
    final NotificationListener listener = notification.getListener();
    if (listener == null) return null;

    return new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final NotificationListener listener1 = notification.getListener();
          if (listener1 != null) {
            NotificationCollector.getInstance().logHyperlinkClicked(notification);
            listener1.hyperlinkUpdate(notification, e);
          }
        }
      }
    };
  }

  @NotNull
  public static Icon getIcon(@NotNull final Notification notification) {
    Icon icon = notification.getIcon();
    if (icon != null) {
      return icon;
    }

    switch (notification.getType()) {
      case WARNING:
        return AllIcons.General.BalloonWarning;
      case ERROR:
        return AllIcons.General.BalloonError;
      case INFORMATION:
      default:
        return AllIcons.General.BalloonInformation;
    }
  }

  @NotNull
  public static MessageType getMessageType(@NotNull Notification notification) {
    switch (notification.getType()) {
      case WARNING:
        return MessageType.WARNING;
      case ERROR:
        return MessageType.ERROR;
      case INFORMATION:
      default:
        return MessageType.INFO;
    }
  }
}
