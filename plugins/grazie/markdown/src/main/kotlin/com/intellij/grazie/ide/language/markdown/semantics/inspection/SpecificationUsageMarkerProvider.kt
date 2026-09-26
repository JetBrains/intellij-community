package com.intellij.grazie.ide.language.markdown.semantics.inspection

import com.intellij.application.options.editor.GutterIconsConfigurable
import com.intellij.codeInsight.daemon.GutterName
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.ide.language.markdown.semantics.analyzer.SpecificationAnalyzer
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isAnalysisAvailable
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isAnalysisEnabled
import com.intellij.grazie.ide.language.markdown.semantics.utils.SpecificationUtils.isSpecificationLikeFile
import com.intellij.grazie.ide.ui.proofreading.ProofreadConfigurable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import java.time.format.DateTimeFormatter
import java.util.function.Supplier
import javax.swing.Icon

private val costTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal class SpecificationUsageMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName(): @GutterName String = GrazieBundle.message("specification.gutter.progress.text")
  override fun getIcon(): Icon = GrazieIcons.Stroke.GrazieCloudProcessing

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    val file = element as? MarkdownFile ?: return null
    if (MarkdownCodeFenceUtils.getCodeFence(file) != null || !isSpecificationLikeFile(file) || !isAnalysisAvailable()) return null
    return UsageMarkerInfo(file, isAnalysisEnabled())
  }

  private class UsageMarkerInfo(file: MarkdownFile, private val analysisEnabled: Boolean) :
    LineMarkerInfo<MarkdownFile>(
      file, file.textRange,
      GrazieIcons.Stroke.GrazieCloudProcessing,
      { GrazieBundle.message(if (analysisEnabled) "specification.gutter.progress.tooltip" else "specification.gutter.setting.tooltip") }, null,
      GutterIconRenderer.Alignment.LEFT,
      { GrazieBundle.message(if (analysisEnabled) "specification.gutter.progress.tooltip" else "specification.gutter.setting.tooltip") }
    ) {
    override fun getLineMarkerTooltip(): @NlsContexts.Tooltip String? {
      if (!analysisEnabled) return GrazieBundle.message("specification.gutter.setting.tooltip")
      val file = element as? PsiFile ?: return null
      val costs = SpecificationAnalyzer.getCosts(file)
        ?: return GrazieBundle.message("specification.gutter.progress.tooltip")
      return GrazieBundle.message(
        "specification.gutter.progress.tooltip.cost",
        costTimeFormatter.format(costs.since),
        costs.credits,
      )
    }

    override fun createGutterRenderer(): GutterIconRenderer {
      return object : LineMarkerGutterIconRenderer<MarkdownFile>(this) {
        override fun isNavigateAction() = true
        override fun getPopupMenuActions(): ActionGroup {
          val actions = mutableListOf<AnAction>(ShowSettingsAction(
            GrazieBundle.messagePointer("specification.gutter.progress.disable"),
            GutterIconsConfigurable::class.java
          ))
          if (!analysisEnabled) {
            actions.addFirst(ShowSettingsAction(
              GrazieBundle.messagePointer("specification.gutter.setting.enable"),
              ProofreadConfigurable::class.java
            ))
          }
          return DefaultActionGroup(actions)
        }
      }
    }
  }

  private class ShowSettingsAction<T : SearchableConfigurable>(
    dynamicText: Supplier<String>, private val toSelect: Class<T>
  ) : DumbAwareAction(dynamicText) {
    override fun actionPerformed(e: AnActionEvent) =
      ShowSettingsUtil.getInstance().showSettingsDialog(e.project, toSelect)
  }
}
