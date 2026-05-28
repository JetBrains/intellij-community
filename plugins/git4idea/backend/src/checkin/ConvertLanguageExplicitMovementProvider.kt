// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import git4idea.i18n.GitBundle

abstract class ConvertLanguageExplicitMovementProvider(
  private val sourceLanguageExtension: @NlsSafe String,
  private val targetLanguageExtension: @NlsSafe String,
  private val userDataKey: Key<String>
) : GitCheckinExplicitMovementProvider() {

  override fun isEnabled(project: Project): Boolean {
    return true
  }

  override fun getDescription(): String {
    return GitBundle.message("convert.language.extra.commit.description", sourceLanguageExtension, targetLanguageExtension)
  }

  override fun getCommitMessage(originalCommitMessage: String): String {
    return GitBundle.message("convert.language.extra.commit.commit.message", sourceLanguageExtension, targetLanguageExtension)
  }

  override fun collectExplicitMovements(
    project: Project,
    beforePaths: List<FilePath>,
    afterPaths: List<FilePath>
  ): Collection<Movement> {
    val beforeWasSourceLanguage = beforePaths.any { it.path.endsWith(".$sourceLanguageExtension") }
    val afterWasTargetLanguage = afterPaths.any { it.path.endsWith(".$targetLanguageExtension") }
    if (!beforeWasSourceLanguage || !afterWasTargetLanguage) {
      return emptyList()
    }

    val movedChanges = ArrayList<Movement>()
    for (after in afterPaths) {
      val pathBeforeConversion = after.virtualFile?.getUserData(userDataKey) ?: continue
      val before = beforePaths.firstOrNull { it.path == pathBeforeConversion } ?: continue
      movedChanges.add(Movement(before, after))
    }

    // avoid processing huge changes
    if (beforePaths.size > 1000) return movedChanges
    val existing = movedChanges.toSet()
    val map = HashMap<String, FilePath>()
    for (before in beforePaths) {
      if (!before.path.endsWith(".$sourceLanguageExtension")) continue
      map[before.path.dropLast(sourceLanguageExtension.length) + targetLanguageExtension] = before
    }
    for (after in afterPaths) {
      val before = map[after.path] ?: continue
      val movement = Movement(before, after)
      if (existing.contains(movement)) continue
      movedChanges.add(movement)
    }
    return movedChanges
  }

  override fun afterMovementsCommitted(project: Project, movedPaths: MutableList<Couple<FilePath>>) {
    movedPaths.forEach { it.second.virtualFile?.putUserData(userDataKey, null) }
  }
}
