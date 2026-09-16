// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.statistics.devkit.actions.scheme

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

internal val EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY = Key.create<Boolean>("statistics.events.test.scheme.validation.rules.file")

private val utilsRules = hashSetOf("class_name", "lang", "plugin_type", "plugin", "plugin_version", "current_file", "place",
                                   "hash", "shortcut", "file_type", "action", "toolwindow")
internal val PREFIXES = listOf("{util#}", "{util:}", "{enum#}", "{enum:}", "{regexp#}", "{regexp:}", "{default_value:}", "{required:}")
internal val TOP_LEVEL_PROPERTIES = listOf("event_id", "event_data")

internal class ValidationRulesCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val file = parameters.originalFile
    if (file.virtualFile?.getUserData(EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY) != true) return

    val element = parameters.position
    val parent = element.originalElement.parent as? JsonStringLiteral ?: return

    val dataFieldProperty = PsiTreeUtil.getParentOfType(parent, JsonProperty::class.java, true) ?: return
    if (JsonPsiUtil.isPropertyKey(parent)) {
      if (dataFieldProperty.parent.parent is JsonFile) {
        result.addAllElements(TOP_LEVEL_PROPERTIES.map { LookupElementBuilder.create(it) })
      }
      return
    }
    val dataProperty = PsiTreeUtil.getParentOfType(dataFieldProperty, JsonProperty::class.java, true)
    if (dataFieldProperty.name != "event_id" && dataProperty?.name != "event_data") return
    val resultSet = patchPrefix(element, parameters, result)

    val prefixVariants = PREFIXES.map {
      LookupElementBuilder.create(it)
        .withInsertHandler(InsertHandler { context, _ ->
          EditorModificationUtil.moveCaretRelatively(context.editor, -1)
        })
    }
    resultSet.addAllElements(prefixVariants)

    val commonRules = file.getUserData(EventsTestSchemeGroupConfiguration.FUS_TEST_SCHEME_COMMON_RULES_KEY)
    if (commonRules != null) {
      result.addAllElements(commonRules.enums.map { LookupElementBuilder.create("{enum#$it}") })
      result.addAllElements(commonRules.regexps.map { LookupElementBuilder.create("{regexp#$it}") })
    }
    resultSet.addAllElements(utilsRules.map { LookupElementBuilder.create("{util#$it}") })
  }

  private fun patchPrefix(element: @NotNull PsiElement,
                          parameters: CompletionParameters,
                          result: CompletionResultSet): CompletionResultSet {
    val patchedPrefix: String = element.text.substring(1, parameters.offset - element.textRange.startOffset)
    return if (patchedPrefix.isBlank()) {
      result
    }
    else {
      result.withPrefixMatcher(patchedPrefix)
    }
  }

}
