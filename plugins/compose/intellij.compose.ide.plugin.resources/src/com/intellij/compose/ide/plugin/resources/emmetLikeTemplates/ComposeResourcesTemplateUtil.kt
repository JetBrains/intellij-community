// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.command.CommandProcessor

private const val MAX_REPETITIONS = 100
internal const val MAX_CHILDREN = 100


private val textRegex = Regex("\\{(?<text>.*)}")
private val repetitionRegex = Regex("""\*(?<count>\d+)""")
internal val templateShortcutRegex = Regex("""^(?<num>\d+)(?<type>[sd]?)$""")

internal class ParsedKey(val parsedKey: String, val text: String, val repetitions: Int)


internal class TemplateBuilder {

  class Result(val text: String, val counter: Int)

  class Context private constructor() {

    var counter: Int = 0
      private set

    fun nextVariable(): String = $$"$V$$counter$".also { counter++ }

    fun getTemplateText(key: String, callback: CustomTemplateCallback): String {
      val parsed = parseKey(key)
      val type = TemplateType.fromParsedKey(parsed.parsedKey)
      return type.makeTemplateText(parsed, callback, this)
    }

    fun resolveName(baseName: String, index: Int): String =
      when {
        baseName.isBlank() -> nextVariable()
        index > 0 -> "$baseName$index"
        else -> baseName
      }

    companion object {
      internal fun buildTemplate(block: Context.() -> String): Result {
        val context = Context()
        val text = context.block()
        return Result(text, context.counter)
      }

      internal fun createTemplateFromKey(key: String, callback: CustomTemplateCallback) {
        val project = callback.project
        val manager = TemplateManager.getInstance(project)
        val context = Context()
        val templateText = context.getTemplateText(key, callback)
        val template = manager.createTemplate(
          "compose_resources_string_template",
          "compose_resources",
          templateText
        ).apply { isToReformat = true }

        context.registerTemplateVariables(template)
        clearCommand(callback, key)
        callback.startTemplate(template, null, null)
      }
    }
  }
}


internal fun tryExpandNumericShortcut(key: String, callback: CustomTemplateCallback): Boolean {
  val match = templateShortcutRegex.find(key) ?: return false

  val num = match.groups["num"]!!.value
  val type = match.groups["type"]!!.value.ifEmpty { "s" }
  val replacement = "%$num$$type"

  val editor = callback.editor
  val offset = editor.caretModel.offset
  val start = offset - key.length

  editor.document.replaceString(start, offset, replacement)
  editor.caretModel.moveToOffset(start + replacement.length)

  return true
}


private fun parseKey(key: String): ParsedKey {
  val textMatch = textRegex.find(key)
  val text = textMatch?.groups?.get("text")?.value ?: ""
  val keyWithoutText = key.replace(textRegex, "")

  val repMatch = repetitionRegex.find(keyWithoutText)
  val repetitionsInput = repMatch?.groups?.get("count")?.value?.toIntOrNull() ?: 1
  val repetitions = minOf(repetitionsInput, MAX_REPETITIONS)
  val parsedKey = keyWithoutText.replace(repetitionRegex, "")

  return ParsedKey(parsedKey, text, repetitions)
}


private inline fun repeatTemplate(times: Int, action: (Int) -> String): String = buildString {
  repeat(times) { i ->
    append(action(i))
    append("\n\t")
  }
  append($$"$END$")
}

internal fun TemplateBuilder.Context.makeStringTemplate(name: String, text: String, repetitions: Int): String =
  repeatTemplate(repetitions) { i ->
    val nameVal = resolveName(name, i)
    val textVal = if (repetitions == 1) text.ifBlank { nextVariable() } else "$text${nextVariable()}"
    "<string name=\"$nameVal\">$textVal</string>"
  }

internal fun TemplateBuilder.Context.makeStringArrayTemplate(name: String, itemNum: Int, text: String, repetitions: Int): String =
  repeatTemplate(repetitions) { i ->
    val nameVal = resolveName(name, i)
    val items = makeItemTemplate(false, text, itemNum).removeSuffix($$"$END$")
    val endPlaceholder = if (itemNum < 2) $$"$END$\n" else ""
    "<string-array name=\"$nameVal\">\n$items$endPlaceholder</string-array>"
  }

internal fun TemplateBuilder.Context.makePluralsTemplate(
  name: String,
  quantityTag: String,
  text: String,
  repetitions: Int,
  qualifier: String,
): String =
  repeatTemplate(repetitions) { i ->
    val nameVal = resolveName(name, i)
    val items = getComposeResourcesPluralQuantityTags(quantityTag, qualifier).joinToString("\n") { q ->
      "<item quantity=\"$q\">$text${nextVariable()}</item>"
    }
    "<plurals name=\"$nameVal\">\n$items\n</plurals>"
  }

internal fun TemplateBuilder.Context.makeItemTemplate(hasQuantity: Boolean, text: String, repetitions: Int): String =
  repeatTemplate(repetitions) { _ ->
    val quantityAttr = if (hasQuantity) " quantity=\"${nextVariable()}\"" else ""
    val textVal = if (repetitions == 1) text.ifBlank { nextVariable() } else "$text${nextVariable()}"
    "<item$quantityAttr>$textVal</item>"
  }


/**
 * When a command is typed, it should be deleted before applying the template.
 */
private fun clearCommand(callback: CustomTemplateCallback, key: String) {
  val editor = callback.editor
  val document = editor.document
  val end = editor.caretModel.offset
  val start = end - key.length

  if (start < 0) return

  CommandProcessor.getInstance().runUndoTransparentAction {
    document.deleteString(start, end)
    editor.caretModel.moveToOffset(start)
  }
}

private fun TemplateBuilder.Context.registerTemplateVariables(template: Template) {
  repeat(counter) { i ->
    template.addVariable("V$i", "", "\"\"", true)
  }
}