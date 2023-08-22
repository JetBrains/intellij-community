// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util

import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection

@Suppress("HardCodedStringLiteral") // it is the internal action, so localization is not required
@ApiStatus.Internal
open class DumpInvalidTipsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    runBackgroundableTask("Analyzing tips", e.getData(CommonDataKeys.PROJECT)) {
      val registry = ProductivityFeaturesRegistry.getInstance() ?: error("ProductivityFeaturesRegistry is not created")
      val productivityGuideTips = registry.featureIds.map { id ->
        val tipId = registry.getFeatureDescriptor(id).tipId
        TipAndTrickBean().also { it.fileName = tipId + TipAndTrickBean.TIP_FILE_EXTENSION }
      }
      val allTips = TipAndTrickBean.EP_NAME.extensionList.plus(productivityGuideTips).distinctBy(TipAndTrickBean::fileName)
      dumpInvalidTips(allTips)
    }
  }

  protected fun dumpInvalidTips(tips: List<TipAndTrickBean>) {
    val tipToError: List<Pair<TipAndTrickBean, Throwable>> = tips.mapNotNull { tip ->
      try {
        @Suppress("TestOnlyProblems")
        TipUtils.loadAndParseTipStrict(tip)
        null
      }
      catch (throwable: Throwable) {
        tip to throwable
      }
    }

    val builder = StringBuilder()
    if (tipToError.isEmpty()) {
      builder.append("There is no invalid tips among ${tips.size} listed")
    }
    else {
      builder.append("Found following problems during tips loading and parsing:\n")
      for (ind in tipToError.indices) {
        val (tip, throwable) = tipToError[ind]
        val message = if (throwable.message?.startsWith("Warning:") == true) {
          throwable.message
        }
        else throwable.stackTraceToString()
        builder.append("${ind + 1}. ${tip.fileName.substringAfterLast("/")}\n$message\n")
      }
    }

    val issues = builder.toString()
    LOG.warn(issues)
    CopyPasteManager.getInstance().setContents(StringSelection(issues))
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val LOG = Logger.getInstance(DumpInvalidTipsAction::class.java)
  }
}

@Suppress("HardCodedStringLiteral") // it is the internal action, so localization is not required
@ApiStatus.Internal
class SelectAndDumpInvalidTipsAction : DumpInvalidTipsAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
      .withFileFilter { it.isDirectory || it.extension == "html" || it.extension == "htm" }
      .withDescription("Choose HTML files or folders with tips.")
    val chosenFiles = FileChooser.chooseFiles(descriptor, project, null)

    runBackgroundableTask("Analyzing tips", project) {
      val tipFiles = mutableListOf<VirtualFile>()
      chosenFiles.forEach { collectTipFilesRecursively(it, tipFiles) }
      val tips = tipFiles.map { file -> TipAndTrickBean().apply { fileName = file.path } }
      dumpInvalidTips(tips)
    }
  }

  private fun collectTipFilesRecursively(file: VirtualFile, list: MutableList<VirtualFile>) {
    if (file.isDirectory) {
      file.children.forEach { collectTipFilesRecursively(it, list) }
    }
    else if (file.extension == "html" || file.extension == "htm") {
      list.add(file)
    }
  }
}

