package com.intellij.driver.sdk.ui

import java.awt.Component

object Locators {
  const val ATTR_ACCESSIBLE_NAME = "@accessiblename"
  const val ATTR_CLASS = "@class"
  const val ATTR_TITLE = "@title"
  const val ATTR_VISIBLE_TEXT = "@visible_text"
  const val ATTR_TOOLTIP = "@tooltiptext"
  const val ATTR_JAVA_CLASS = "@javaclass"

  fun byAccessibleName(name: String): String = byAttribute(ATTR_ACCESSIBLE_NAME, name)
  fun byAccessibleNameContains(name: String): String = byAttributeContains(ATTR_ACCESSIBLE_NAME, name)
  fun byVisibleText(text: String): String = byAttribute(ATTR_VISIBLE_TEXT, text)
  fun byVisibleTextContains(text: String): String = byAttributeContains(ATTR_VISIBLE_TEXT, text)
  fun byTitle(title: String): String = byAttribute(ATTR_TITLE, title)
  fun byTitleContains(title: String): String = byAttributeContains(ATTR_TITLE, title)
  fun byClass(cls: String): String = byAttribute(ATTR_CLASS, cls)
  fun byTooltip(tooltip: String): String = byAttribute(ATTR_TOOLTIP, tooltip)
  fun byClassAndAccessibleName(cls: String, accessibleName: String) =
    byAttributes(ATTR_CLASS to cls, ATTR_ACCESSIBLE_NAME to accessibleName)
  fun <T : Component> byType(type: Class<T>) = byType(type.name)
  fun byType(type: String) = """//div[@javaclass="$type" or contains(@classhierarchy, "$type ") or contains(@classhierarchy, " $type ")]"""
  fun byJavaClassContains(type: String) = byAttributeContains(ATTR_JAVA_CLASS, type)
  fun byAttribute(name: String, value: String) = byAttributes(name to value)
  fun byAttributes(attr: Pair<String, String>, vararg attrs: Pair<String, String>) =
    "//div[${listOf(attr, *attrs).joinToString(" and ") { "${it.first}='${it.second}'" }}]"
  fun byAttributeContains(name: String, value: String) = "//div[contains($name,'$value')]"
  fun componentWithChild(componentLocator: String, childLocator: String) = "${componentLocator}[.${childLocator}]"
}