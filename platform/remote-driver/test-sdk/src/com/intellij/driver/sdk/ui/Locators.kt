package com.intellij.driver.sdk.ui

import java.awt.Component

object Locators {
  fun byAccessibleName(name: String): String = "//div[@accessiblename='$name']"
  fun byClass(cls: String): String = "//div[@class='$cls']"
  fun <T : Component> byType(type: Class<T>) = byType(type.name)
  fun byType(type: String) = """//div[@javaclass="$type" or contains(@classhierarchy, "$type") or contains(@classhierarchy, " $type ")]"""
}