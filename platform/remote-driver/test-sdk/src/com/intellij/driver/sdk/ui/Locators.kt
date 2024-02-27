package com.intellij.driver.sdk.ui

import java.awt.Component

object Locators {
  fun byAccessibleName(name: String): String = "//div[@accessiblename='$name']"
  fun byTitle(title: String): String = byAttribute("@title", title)
  fun byTitleContains(title: String): String = byAttributeContains("@title", title)
  fun byClass(cls: String): String = byAttribute("@class", cls)
  fun <T : Component> byType(type: Class<T>) = byType(type.name)
  fun byType(type: String) = """//div[@javaclass="$type" or contains(@classhierarchy, "$type") or contains(@classhierarchy, " $type ")]"""
  fun byAttribute(name: String, value: String) = "//div[$name='$value']"
  fun byAttributeContains(name: String, value: String) = "//div[contains($name,'$value')]"
}