package com.intellij.grazie.markdown.specification.utils

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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.hasMarkdownType
import java.util.concurrent.Semaphore

internal object SpecificationUtils {
  private const val SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED = "grazie.promote.specification.analysis.dismissed"
  private val SPECIFICATION_ANALYSIS_PROMOTION_LOCK = Key.create<Semaphore>("grazie.specification.analysis.promotion.lock")
  private const val SPECIFICATION_PATTERN_REGISTRY_KEY = "grazie.specification.semantics.specification.pattern"

  private val SPECIFICATION_LIKE_PATTERN = Regex(
    "(agents?|ai|architecture|checklist|claude|commands|copilot-instructions|design|" +
      "implementation|instructions|memory|plans?|prompts?|references|research|requirements|session|" +
      "skills?|specs?|summary|system([-_]prompt)?|tasks?|todo|tool|workflows?)\\.md",
    RegexOption.IGNORE_CASE,
  )

  private const val SKILL_FILE_NAME = "SKILL.md"
  private const val SKILL_REFERENCES_DIRECTORY = "references"

  internal fun isAnalysisAvailable(): Boolean = seemsCloudConnected() && hasQuota()

  internal fun isAnalysisEnabled(): Boolean = isAnalysisAvailable() && GrazieConfig.get().specificationAnalysisEnabled

  internal fun isSpecificationLikeFile(file: PsiFile): Boolean {
    return isSpecificationLikeFile(file.virtualFile)
  }

  internal fun isSpecificationLikeFile(file: VirtualFile): Boolean {
    return isSpecificationLikeFile(file, getSpecificationLikePattern())
  }

  internal fun isSpecificationLikeFile(file: VirtualFile, specificationPattern: Regex?): Boolean {
    return SPECIFICATION_LIKE_PATTERN.matches(file.name) || specificationPattern?.matches(file.path) == true
  }

  internal fun getSpecificationLikePattern(): Regex? {
    return Registry.stringValue(SPECIFICATION_PATTERN_REGISTRY_KEY, "")
      .takeIf { it.isNotEmpty() }
      ?.let(::Regex)
  }

  /**
   * Returns `true` when [file] is the `SKILL.md` file of a skill, or a Markdown file in the `references` folder of that skill.
   *
   * See the [Agent Skills specification](https://agentskills.io/specification#references).
   */
  internal fun isSkillFile(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return isSkillFile(virtualFile)
  }

  internal fun isSkillFile(file: VirtualFile): Boolean {
    return findSkillFile(file) != null
  }

  /**
   * Returns the whole skill that [file] belongs to. The result holds the `SKILL.md` file and every Markdown file in the
   * `references` folder next to it.
   *
   * The result is empty when [file] is not a part of a skill.
   */
  internal fun getSkillFiles(file: VirtualFile): List<VirtualFile> {
    val skillFile = findSkillFile(file) ?: return emptyList()
    val directory = skillFile.parent?.findChild(SKILL_REFERENCES_DIRECTORY)?.takeIf { it.isDirectory }
    val references = directory?.children?.filter { !it.isDirectory && it.hasMarkdownType() }.orEmpty()
    return references + skillFile
  }

  private fun findSkillFile(file: VirtualFile): VirtualFile? {
    if (file.name.equals(SKILL_FILE_NAME, ignoreCase = true)) return file
    val directory = file.parent ?: return null
    if (!directory.name.equals(SKILL_REFERENCES_DIRECTORY, ignoreCase = true)) return null
    return directory.parent?.findChild(SKILL_FILE_NAME)
  }

  internal fun promoteAnalyzers(holder: ProblemsHolder, file: PsiFile) {
    if (PropertiesComponent.getInstance().getBoolean(SPECIFICATION_ANALYSIS_PROMOTION_DISMISSED)) return

    val lock = (file as UserDataHolderEx).putUserDataIfAbsent(SPECIFICATION_ANALYSIS_PROMOTION_LOCK, Semaphore(1))
    if (!lock.tryAcquire()) return
    try {
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
