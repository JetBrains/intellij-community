package com.intellij.grazie.ide.language.markdown.semantics.utils

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.hasQuota
import com.intellij.grazie.cloud.GrazieCloudConnector.Companion.seemsCloudConnected
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import java.util.concurrent.Semaphore

internal object SpecificationUtils {
  private const val SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED = "grazie.promote.specification.analysis.dismissed"
  private val SPECIFICATION_ANALYSIS_PROMOTION_LOCK = Key.create<Semaphore>("grazie.specification.analysis.promotion.lock")

  private val SPECIFICATION_LIKE_PATTERN = Regex(
    "(agents|agent|ai|claude|copilot-instructions|prompt|skill|system[-_]prompt|spec|architecture)\\.md",
    RegexOption.IGNORE_CASE,
  )

  internal fun isAnalysisAvailable(): Boolean = seemsCloudConnected() && hasQuota()

  internal fun isAnalysisEnabled(): Boolean = isAnalysisAvailable() && GrazieConfig.get().specificationAnalysisEnabled

  internal fun isSpecificationLikeFile(file: PsiFile): Boolean {
    if (SPECIFICATION_LIKE_PATTERN.matches(file.name)) return true
    val pattern = Regex(Registry.stringValue("grazie.specification.semantics.specification.pattern"))
    return pattern.matches(file.virtualFile.path)
  }

  internal fun promoteAnalyzers(holder: ProblemsHolder, file: PsiFile) {
    if (PropertiesComponent.getInstance().getBoolean(SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED)) return

    val lock = (file as UserDataHolderEx).putUserDataIfAbsent(SPECIFICATION_ANALYSIS_PROMOTION_LOCK, Semaphore(1))
    try {
      if (!lock.tryAcquire()) return
      if (PropertiesComponent.getInstance().getBoolean(SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED)) return

      val actions = arrayOf(
        object : LocalQuickFix {
          override fun getFamilyName(): @IntentionFamilyName String = GrazieBundle.message("specification.quick.fix.specification.enable")
          override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            GrazieConfig.update { it.copy(specificationAnalysisEnabled = true) }
            PropertiesComponent.getInstance().updateValue(SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED, true)
          }
        },
        object : LocalQuickFix {
          override fun getFamilyName(): @IntentionFamilyName String = GrazieBundle.message("specification.quick.fix.specification.dismiss")
          override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            PropertiesComponent.getInstance().updateValue(SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED, true)
          }
        }
      )
      holder.registerProblem(
        file, GrazieBundle.message("specification.quick.fix.specification.promotion"),
        ProblemHighlightType.INFORMATION, *actions
      )
    } finally {
      lock.release()
    }
  }
}