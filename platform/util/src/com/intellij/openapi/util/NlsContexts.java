// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * See the <a href="https://jetbrains.design/intellij/">IntelliJ Platform UI Guidelines</a>.
 */
public final class NlsContexts {
  /**
   * Dialogs
   */
  @NlsContext(prefix = "dialog.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface DialogTitle { }

  @NlsContext(prefix = "dialog.message")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.LOCAL_VARIABLE})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface DialogMessage { }

  /**
   * Popups
   */
  @NlsContext(prefix = "popup.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface PopupTitle { }

  @NlsContext(prefix = "popup.content")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface PopupContent { }

  @NlsContext(prefix = "popup.advertisement")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface PopupAdvertisement { }

  /**
   * Notifications
   */
  @NlsContext(prefix = "notification.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface NotificationTitle { }

  @NlsContext(prefix = "notification.subtitle")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface NotificationSubtitle { }

  @NlsContext(prefix = "notification.content")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface NotificationContent { }

  @NlsContext(prefix = "status.text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface StatusText { }

  @NlsContext(prefix = "hint.text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface HintText { }

  @NlsContext(prefix = "configurable.name")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface ConfigurableName { }

  @NlsContext(prefix = "parsing.error")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ParsingError { }

  @NlsContext(prefix = "status.bar.text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface StatusBarText { }

  /**
   * Use it for annotating OS provided notification title, such as "project built" or "tests running finished".
   * See also #SystemNotificationText.
   */
  @NlsContext(prefix = "system.notification.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface SystemNotificationTitle { }

  /**
   * Use it for annotating OS provided notification content.
   * See also #SystemNotificationTitle.
   */
  @NlsContext(prefix = "system.notification.text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface SystemNotificationText { }

  @NlsContext(prefix = "command.name")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface Command { }

  @NlsContext(prefix = "tab.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface TabTitle { }

  /**
   * Annotate by {@code #AttributeDescriptor} text attribute keys, see {@link com.intellij.openapi.options.colors.AttributesDescriptor}
   */
  @NlsContext(prefix = "attribute.descriptor")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface AttributeDescriptor { }

  @NlsContext(prefix = "column.name")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface ColumnName { }

  /**
   * Swing components
   */
  @NlsContext(prefix = "label")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface Label { }

  @NlsContext(prefix = "link.label")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface LinkLabel { }

  @NlsContext(prefix = "checkbox")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface Checkbox { }

  @NlsContext(prefix = "radio")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface RadioButton { }

  @NlsContext(prefix = "border.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface BorderTitle { }

  @NlsContext(prefix = "tooltip")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface Tooltip { }

  @NlsContext(prefix = "separator")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface Separator { }

  @NlsContext(prefix = "button")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
  public @Nls(capitalization = Nls.Capitalization.Title) @interface Button { }

  @NlsContext(prefix = "text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface DetailedDescription { }

  @NlsContext(prefix = "list.item")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ListItem { }

  @NlsContext(prefix = "progress.text")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ProgressText { }

  @NlsContext(prefix = "progress.details")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ProgressDetails { }

  @NlsContext(prefix = "progress.title")
  @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
  public @Nls(capitalization = Nls.Capitalization.Sentence) @interface ProgressTitle { }
}
