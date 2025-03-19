package com.intellij.driver.sdk.ui

import java.awt.Component

/**
 * See [com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator]
 */
class QueryBuilder {
  fun byAccessibleName(name: String) = byAttribute("accessiblename", name)

  fun byVisibleText(text: String) = byAttribute("visible_text", text)

  fun byTitle(title: String) = byAttribute("title", title)

  fun byClass(cls: String) = byAttribute("class", cls)

  fun byJavaClass(cls: String) = byAttribute("javaclass", cls)

  fun byType(type: String) = or(byJavaClass(type), contains(byAttribute("classhierarchy", "$type ")), contains(byAttribute("classhierarchy", " $type ")))

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
