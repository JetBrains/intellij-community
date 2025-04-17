// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.annotations.Nls
import kotlin.annotation.AnnotationTarget.*

/**
 * See the [IntelliJ Platform UI Guidelines](https://plugins.jetbrains.com/docs/intellij/capitalization.html).
 */
class NlsContexts {
  /**
   * Dialogs
   */
  @NlsContext(prefix = "dialog.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class DialogTitle

  @NlsContext(prefix = "dialog.message")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, LOCAL_VARIABLE)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class DialogMessage

  /**
   * Popups
   */
  @NlsContext(prefix = "popup.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class PopupTitle

  @NlsContext(prefix = "popup.content")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class PopupContent

  @NlsContext(prefix = "popup.advertisement")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class PopupAdvertisement

  /**
   * Notifications
   */
  @NlsContext(prefix = "notification.on.tool.window.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class NotificationTitle

  @NlsContext(prefix = "notification.subtitle")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class NotificationSubtitle

  @NlsContext(prefix = "notification.content")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class NotificationContent

  @NlsContext(prefix = "status.text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class StatusText

  @NlsContext(prefix = "hint.text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class HintText

  @NlsContext(prefix = "configurable.name")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class ConfigurableName

  @NlsContext(prefix = "parsing.error")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class ParsingError

  @NlsContext(prefix = "status.bar.text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class StatusBarText

  /**
   * Use it for annotating OS provided notification title, such as "project built" or "tests running finished".
   * See also #SystemNotificationText.
   */
  @NlsContext(prefix = "system.notification.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class SystemNotificationTitle

  /**
   * Use it for annotating OS provided notification content.
   * See also #SystemNotificationTitle.
   */
  @NlsContext(prefix = "system.notification.text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class SystemNotificationText

  @NlsContext(prefix = "command.name")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  annotation class Command

  @NlsContext(prefix = "tab.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class TabTitle

  /**
   * Annotate by `#AttributeDescriptor` text attribute keys, see [com.intellij.openapi.options.colors.AttributesDescriptor]
   */
  @NlsContext(prefix = "attribute.descriptor")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class AttributeDescriptor

  @NlsContext(prefix = "column.name")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class ColumnName

  /**
   * Swing components
   */
  @NlsContext(prefix = "label")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class Label

  @NlsContext(prefix = "link.label")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class LinkLabel

  @NlsContext(prefix = "checkbox")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class Checkbox

  @NlsContext(prefix = "radio")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class RadioButton

  @NlsContext(prefix = "border.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class BorderTitle

  @NlsContext(prefix = "tooltip")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class Tooltip

  @NlsContext(prefix = "separator")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class Separator

  @NlsContext(prefix = "button")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, FIELD)
  @Nls(capitalization = Nls.Capitalization.Title)
  annotation class Button

  @NlsContext(prefix = "text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class DetailedDescription

  @NlsContext(prefix = "list.item")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class ListItem

  @NlsContext(prefix = "progress.text")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class ProgressText

  @NlsContext(prefix = "progress.details")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class ProgressDetails

  @NlsContext(prefix = "progress.title")
  @Target(TYPE, TYPE_PARAMETER, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  annotation class ProgressTitle
}
