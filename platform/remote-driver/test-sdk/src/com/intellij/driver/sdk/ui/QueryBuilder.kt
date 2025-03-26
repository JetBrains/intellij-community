package com.intellij.driver.sdk.ui

import org.intellij.lang.annotations.Language
import java.awt.Component

/**
 * See [com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator]
 */
class QueryBuilder {
  fun byAccessibleName(name: String) = byAttribute("accessiblename", name)

  fun byVisibleText(text: String) = byAttribute("visible_text", text)

  fun byTitle(title: String) = byAttribute("title", title)

  /**
   * Builds a query to search for components by their short class name (without package and removed parts after `$`) if the class is not anonymous.
   * Otherwise, it's a short name of the parent class.
   * Example: `byClass("JBList")`
   */
  fun byClass(cls: String) = byAttribute("class", cls)

  /**
   * Builds a query to search for components by their full class name (including package).
   * Example: `byJavaClass("com.intellij.ui.components.JBList")`
   */
  fun byJavaClass(@Language("jvm-class-name") cls: String) = byAttribute("javaclass", cls)

  /**
   * Builds a query to search for components either by their full class name or by parent type.
   * This method matches:
   * - Elements with the exact Java class
   * - Elements that have the specified type in their class hierarchy
   *
   * Example: `byType("com.intellij.ui.components.JBList")`
   */
  fun byType(@Language("jvm-class-name") type: String) = or(byJavaClass(type), contains(byAttribute("classhierarchy", "$type ")), contains(byAttribute("classhierarchy", " $type ")))

  /**
   * Builds a query to search for components either by their full class name or by parent type.
   * This method matches:
   * - Elements with the exact Java class
   * - Elements that have the specified type in their class hierarchy
   *
   * Example: `byType(JBList::class.java)`
   */
  fun <T : Component> byType(type: Class<T>) =  byType(type.name)

  fun byTooltip(tooltip: String) = byAttribute("tooltiptext", tooltip)

  fun byAttribute(name: String, value: String) = "@$name=${value.escapeQuoteWithConcat()}"

  fun byText(text: String) = byAttribute("text", text)

  fun contains(condition: String): String {
    val arguments = condition.split("=")
    require(arguments.size == 2) { "contains condition must have format 'attribute=value'" }
    val name = arguments[0]
    val value = arguments[1]
    return "contains($name, $value)"
  }

  fun not(condition: String): String {
    return "not($condition)"
  }

  fun and(condition: String, condition2: String,  vararg conditions: String): String {
    val allConditions = listOf(condition, condition2, *conditions)
    return allConditions.joinToString(" and ", "(",")")
  }

  fun or(condition: String, condition2: String, vararg conditions: String): String {
    val allConditions = listOf(condition, condition2, *conditions)
    return allConditions.joinToString(" or ","(",")")
  }

  fun componentWithChild(componentLocator: String, childLocator: String) = "${componentLocator}][.//div[${childLocator}]"
}

fun xQuery(init: QueryBuilder.() -> String): String {
  return "//div[" + QueryBuilder().init() + "]"
}

fun String.escapeQuoteWithConcat(): String {
  if (!contains("'")) return "'$this'"
  return split("'").joinToString(
    prefix = "concat(",
    postfix = ")",
    separator = ", \"'\", "
  ) {
    if (it.isNotEmpty()) {
      "'$it'"
    } else {
      ""
    }
  }.let {
    if (it.endsWith(", )")) {
      it.substring(0, it.length - 3) + ")"
    } else it
  }.replace("concat(, ", "concat(")
}
