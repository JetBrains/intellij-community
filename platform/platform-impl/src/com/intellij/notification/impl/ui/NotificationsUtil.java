// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationCollector;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.util.List;

public final class NotificationsUtil {
  private static final Logger LOG = Logger.getInstance(NotificationsUtil.class);
  private static final int TITLE_LIMIT = 1000;
  private static final int CONTENT_LIMIT = 10000;

  private static final @NlsSafe String P_TAG = "<p/>";
  private static final @NlsSafe String BR_TAG = "<br>";

  public static @NotNull @Nls String buildHtml(final @NotNull Notification notification,
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
    boolean boldTitle = false;
    if (!Notification.isEmpty(title) || !Notification.isEmpty(subtitle)) {
      boldTitle = !Notification.isEmpty(notification.getContent());
    }
    String colorText = color == null ? null : "#" + ColorUtil.toHex(color);
    return buildHtml(title, subtitle, boldTitle, content, style, isContent ? null : colorText, isContent ? colorText : null, contentStyle);
  }

  public static @NotNull @Nls String buildFullContent(@NotNull Notification notification) {
    String content = StringUtil.replace(notification.getContent(), P_TAG, BR_TAG);
    return buildHtml(null, null, false, content, null, null, null, null);
  }

  public static @NotNull @Nls String buildStatusMessage(@NotNull Notification notification) {
    String title = notification.getTitle();
    String subtitle = notification.getSubtitle();
    if (StringUtil.isNotEmpty(title) && StringUtil.isNotEmpty(subtitle)) {
      title += " (" + subtitle + ")";
    }
    title = StringUtil.first(title, TITLE_LIMIT, true);

    String content = StringUtil.first(notification.getContent(), TITLE_LIMIT, true);

    @NlsSafe String message;
    if (StringUtil.isNotEmpty(title)) {
      message = title;
      if (StringUtil.isNotEmpty(content)) {
        message += ": ";
        message += content;
      }
    }
    else {
      message = content;
    }

    List<AnAction> actions = notification.getActions();
    if (!actions.isEmpty()) {
      message += " // ";
      message += StringUtil.join(actions, action -> action.getTemplateText(), " // ");
    }

    message = StringUtil.replace(message, "<a href=", " // <a href=");
    message = StringUtil.stripHtml(message, " ");
    message = StringUtil.replace(message, "\n", " ");
    message = StringUtil.replace(message, "&nbsp;", " ");
    message = StringUtil.replace(message, "&raquo;", ">>");
    message = StringUtil.replace(message, "&laquo;", "<<");
    message = StringUtil.replace(message, "&hellip;", "...");
    message = StringUtil.unescapeXmlEntities(message);
    message = StringUtil.collapseWhiteSpace(message);

    return message;
  }

  public static @NotNull @Nls String buildHtml(@Nullable @Nls String title,
                                               @Nullable @Nls String subtitle,
                                               boolean boldTitle,
                                               @Nullable @Nls String content,
                                               @Nullable String style,
                                               @Nullable String titleColor,
                                               @Nullable String contentColor,
                                               @Nullable String contentStyle) {
    if (Notification.isEmpty(title) && !Notification.isEmpty(subtitle)) {
      title = subtitle;
      subtitle = null;
    }
    else if (!Notification.isEmpty(title) && !Notification.isEmpty(subtitle)) {
      title += ":";
    }

    HtmlBuilder htmlBuilder = new HtmlBuilder();
    if (!Notification.isEmpty(title)) {
      if (boldTitle) {
        HtmlChunk.Element titleChunk = HtmlChunk.raw(title).bold();
        if (StringUtil.isNotEmpty(titleColor)) {
          titleChunk = titleChunk.attr("color", titleColor);
        }

        htmlBuilder.append(titleChunk);
      }
      else {
        htmlBuilder.append(StringUtil.isNotEmpty(titleColor) ?
                           HtmlChunk.span().attr("color", titleColor).addText(title) :
                           HtmlChunk.raw(title));
      }
    }

    if (!Notification.isEmpty(subtitle)) {
      htmlBuilder.nbsp().append(StringUtil.isNotEmpty(titleColor) ?
                                HtmlChunk.span().attr("color", titleColor).addText(subtitle) :
                                HtmlChunk.raw(subtitle));
    }

    if (!Notification.isEmpty(content)) {
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

  public static @Nullable String getFontStyle() {
    String fontName = getFontName();
    return StringUtil.isEmpty(fontName) ? null : "font-family:" + fontName + ";";
  }

  public static @NotNull Float getFontSize() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideLafFonts()) {
      return uiSettings.getFontSize2D();
    }
    return StartupUiUtil.getLabelFont().getSize2D();
  }

  public static @Nullable String getFontName() {
    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getOverrideLafFonts()) {
      return uiSettings.getFontFace();
    }
    return StartupUiUtil.getLabelFont().getName();
  }

  public static @Nullable HyperlinkListener wrapListener(final @NotNull Notification notification) {
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

  public static void configureHtmlEditorKit(@NotNull JEditorPane editorPane, boolean notificationColor) {
    HTMLEditorKit kit = new HTMLEditorKitBuilder().withWordWrapViewFactory().withFontResolver(new CSSFontResolver() {
      @Override
      public @NotNull Font getFont(@NotNull Font defaultFont, @NotNull AttributeSet attributeSet) {
        if ("a".equalsIgnoreCase(String.valueOf(attributeSet.getAttribute(AttributeSet.NameAttribute)))) {
          return UIUtil.getLabelFont();
        }
        return defaultFont;
      }
    }).build();
    String color = ColorUtil.toHtmlColor(notificationColor ? getLinkButtonForeground() : JBUI.CurrentTheme.Link.Foreground.ENABLED);
    kit.getStyleSheet().addRule("a {color: " + color + "}");
    editorPane.setEditorKit(kit);
  }

  public static @NotNull Color getLinkButtonForeground() {
    return JBColor.namedColor("Notification.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
  }

  public static @NotNull Color getMoreButtonForeground() {
    return JBColor.namedColor("Notification.MoreButton.foreground", new JBColor(0x666666, 0x8C8C8C));
  }

  public static @NotNull Color getMoreButtonBackground() {
    return JBColor.namedColor("Notification.MoreButton.background", new JBColor(0xE3E3E3, 0x3A3C3D));
  }

  public static @NotNull Icon getIcon(@NotNull Notification notification) {
    Icon icon = notification.getIcon();
    if (icon != null) {
      return icon;
    }

    return switch (notification.getType()) {
      case WARNING -> AllIcons.General.BalloonWarning;
      case ERROR -> AllIcons.General.BalloonError;
      case INFORMATION, IDE_UPDATE -> AllIcons.General.BalloonInformation;
    };
  }

  public static @NotNull MessageType getMessageType(@NotNull Notification notification) {
    return switch (notification.getType()) {
      case WARNING -> MessageType.WARNING;
      case ERROR -> MessageType.ERROR;
      case INFORMATION, IDE_UPDATE -> MessageType.INFO;
    };
  }
}
