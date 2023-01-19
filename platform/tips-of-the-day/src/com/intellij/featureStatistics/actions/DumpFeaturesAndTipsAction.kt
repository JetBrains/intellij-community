// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.actions

import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection

class DumpFeaturesAndTipsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    ProductivityFeaturesRegistry.getInstance()?.let { featuresRegistry ->
      val tips = TipAndTrickBean.EP_NAME.extensionList.associateBy { it.fileName }.toMutableMap()
      val rows = mutableListOf<FeatureTipRow>()
      for (featureId in featuresRegistry.featureIds) {
        val featureTipRow = FeatureTipRow(featureId)
        featuresRegistry.getFeatureDescriptor(featureId)?.let { feature ->
          featureTipRow.group = feature.groupId
          featureTipRow.featureProvider = feature.provider?.let { PluginManager.getPluginByClass(it)?.pluginId?.idString }
          TipUtils.getTip(feature)?.let { tip ->
            featureTipRow.tipFile = tip.fileName
            featureTipRow.tipProvider = tip.pluginDescriptor?.pluginId?.idString
            featureTipRow.tipProblem = checkTipLoadingAndParsing(tip)
            tips.remove(tip.fileName)
          }
        }
        rows.add(featureTipRow)
      }
      for (tip in tips.values) {
        rows.add(FeatureTipRow(tipFile = tip.fileName,
                               tipProvider = tip.pluginDescriptor?.pluginId?.idString,
                               tipProblem = checkTipLoadingAndParsing(tip)))
      }
      val sortedRows = rows.sortedWith(compareBy(nullsLast()) { it.group })
      val tipsAndFeaturesTable: String = buildPrettyTable(sortedRows)
      CopyPasteManager.getInstance().setContents(StringSelection(tipsAndFeaturesTable))
    }
  }

  private fun checkTipLoadingAndParsing(tip: TipAndTrickBean): String {
    return try {
      @Suppress("TestOnlyProblems")
      TipUtils.loadAndParseTipStrict(tip)
      ""
    }
    catch (t: Throwable) {
      if (t.message?.startsWith("Warning") == true) "Parsing warning" else "Loading/Parsing error"
    }
  }

  private fun buildPrettyTable(rows: List<FeatureTipRow>): String {
    val columns: List<List<String>> = listOf(
      (0..rows.size).map { it.toString() },
      rows.map(FeatureTipRow::presentableId).toMutableList().also { it.add(0, "Feature ID") },
      rows.map(FeatureTipRow::presentableGroup).toMutableList().also { it.add(0, "Group ID") },
      rows.map(FeatureTipRow::presentableFeatureProvider).toMutableList().also { it.add(0, "Feature Provider") },
      rows.map(FeatureTipRow::presentableTipFile).toMutableList().also { it.add(0, "Tip File") },
      rows.map(FeatureTipRow::presentableTipProvider).toMutableList().also { it.add(0, "Tip Provider") },
      rows.map(FeatureTipRow::tipProblem).toMutableList().also { it.add(0, "Tip Loading/Parsing Problem") },
    )
    val columnSizes = columns.map { col -> col.maxOf { it.length } + 2 }
    return buildString {
      for (rowInd in 0..rows.size) {
        for (colInd in columnSizes.indices) {
          val value = columns[colInd][rowInd]
          append(" ")
          append(value)
          append(" ".repeat(columnSizes[colInd] - value.length + 1))
          append(if (colInd != columnSizes.lastIndex) "|" else "\n")
        }
      }
    }
  }

  private data class FeatureTipRow(var id: String? = null,
                                   var group: String? = null,
                                   var featureProvider: String? = null,
                                   var tipFile: String? = null,
                                   var tipProvider: String? = null,
                                   var tipProblem: String = "") {
    companion object {
      private const val EMPTY_VALUE = "NONE"
      private const val PLATFORM = "platform"
    }

    val presentableId: String
      get() = handleEmpty(id)
    val presentableGroup: String
      get() = handleEmpty(group)
    val presentableFeatureProvider: String
      get() = featureProvider ?: PLATFORM
    val presentableTipFile: String
      get() = handleEmpty(tipFile)
    val presentableTipProvider: String
      get() = handleEmpty(tipProvider)

    private fun handleEmpty(value: String?) = value ?: EMPTY_VALUE
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}