// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.actions

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.util.idString
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

object MLCompletionFeaturesUtil {
  fun getCommonFeatures(lookup: LookupImpl): CommonFeatures {
    val storage = LookupStorage.get(lookup) ?: return CommonFeatures()
    return CommonFeatures(storage.userFactors,
                          storage.sessionFactors.getLastUsedCommonFactors(),
                          storage.contextFactors)
  }

  fun getElementFeatures(lookup: LookupImpl, element: LookupElement): ElementFeatures {
    val id = element.idString()
    val storage = LookupStorage.get(lookup) ?: return ElementFeatures(id)
    val features = storage.getItemStorage(id).getLastUsedFactors() ?: return ElementFeatures(id)
    return ElementFeatures(id, features.mapValues { it.value.toString() })
  }

  data class CommonFeatures(val user: Map<String, String> = emptyMap(),
                            val session: Map<String, String> = emptyMap(),
                            val context: Map<String, String> = emptyMap())

  data class ElementFeatures(val id: String, val features: Map<String, String> = emptyMap())

  class CopyFeaturesToClipboard : AnAction() {
    companion object {
      private val LOG = logger<CopyFeaturesToClipboard>()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val editor = e.getData(CommonDataKeys.EDITOR)
      e.presentation.isEnabled = editor != null && LookupManager.getActiveLookup(editor) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      val editor = e.getData(CommonDataKeys.EDITOR)
      val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return
      val json = JSON.std.with(JSON.Feature.PRETTY_PRINT_OUTPUT).asString(mapOf(
        "common" to getCommonFeatures(lookup),
        "elements" to lookup.items.associate {
          val elementFeatures = getElementFeatures(lookup, it)
          elementFeatures.id to elementFeatures.features
        }
      ))

      try {
        CopyPasteManager.getInstance().setContents(StringSelection(json))
      }
      catch (e: Exception) {
        LOG.debug("Error on copying features to clipboard: $json")
        throw e
      }
    }
  }
}