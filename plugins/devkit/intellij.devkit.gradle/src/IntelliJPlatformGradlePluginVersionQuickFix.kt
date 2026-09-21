// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

internal class IntelliJPlatformGradlePluginVersionQuickFix(
  private val projectPath: String,
  private val currentVersion: String,
  private val latestVersion: String,
) : BuildIssueQuickFix {

  override val id: String = "update_intellij_platform_gradle_plugin"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> = project.gradleCoroutineScope.launch {
    val files = findCandidateFiles(Path.of(projectPath))
    @Suppress("DialogTitleCapitalization")
    val commandName = DevKitGradleBundle.message("intellij.platform.gradle.plugin.outdated.version.quick.fix.command")
    val changed = writeCommandAction(project, commandName) {
      val psiManager = PsiManager.getInstance(project)
      files.mapNotNull(psiManager::findFile).fold(false) { hasChanges, file ->
        updateIntelliJPlatformGradlePluginVersion(file, currentVersion, latestVersion) || hasChanges
      }
    }
    if (!changed) return@launch

    ExternalSystemUtil.requestImport(project, projectPath, GradleConstants.SYSTEM_ID).await()
  }.asCompletableFuture()

  private suspend fun findCandidateFiles(root: Path): List<VirtualFile> = withContext(Dispatchers.IO) {
    val rootFile = root.refreshAndFindVirtualDirectory() ?: return@withContext emptyList()
    buildList {
      VfsUtilCore.iterateChildrenRecursively(rootFile, { file ->
        file == rootFile || !file.name.startsWith('.') && file.name !in EXCLUDED_DIRECTORIES
      }) { file ->
        if (!file.isDirectory && isCandidateFile(file.name)) add(file)
        true
      }
    }
  }

  private fun isCandidateFile(fileName: String): Boolean =
    fileName.endsWith(".gradle.kts") || fileName.endsWith(".versions.toml")

  companion object {
    private val EXCLUDED_DIRECTORIES = setOf("build", "out", "src")
  }
}

internal fun updateIntelliJPlatformGradlePluginVersion(
  file: PsiFile,
  currentVersion: String,
  latestVersion: String,
): Boolean {
  val replacements = findIntelliJPlatformGradlePluginVersionReplacements(file, currentVersion, latestVersion)
  for ((element, newContent) in replacements) {
    ElementManipulators.handleContentChange(element, newContent)
  }
  return replacements.isNotEmpty()
}

internal fun findIntelliJPlatformGradlePluginVersionReplacements(
  file: PsiFile,
  currentVersion: String,
  latestVersion: String,
): Map<PsiElement, String> = when (file) {
  is KtFile -> findKotlinReplacements(file, currentVersion, latestVersion)
  else -> file.project.getService(IntelliJPlatformVersionCatalogUpdater::class.java)
    ?.findReplacements(file, currentVersion, latestVersion)
    .orEmpty()
}

private fun findKotlinReplacements(file: KtFile, currentVersion: String, latestVersion: String): Map<PsiElement, String> {
  return file.descendantsOfType<KtStringTemplateExpression>().mapNotNull { literal ->
    literal.replacement(currentVersion, latestVersion)?.let { literal to it }
  }.toMap()
}

private fun KtStringTemplateExpression.replacement(currentVersion: String, latestVersion: String): String? {
  val value = plainString() ?: return null
  if (value == currentVersion && isIntelliJPlatformPluginVersion()) return latestVersion

  val dependencyPrefix = "$INTELLIJ_PLATFORM_PLUGIN_ID:intellij-platform-gradle-plugin:"
  val call = parents(false).filterIsInstance<KtCallExpression>().firstOrNull()
  return if (call?.calleeExpression?.text == "classpath" && value == dependencyPrefix + currentVersion) dependencyPrefix + latestVersion else null
}

private fun KtStringTemplateExpression.isIntelliJPlatformPluginVersion(): Boolean {
  return parents(false).filterIsInstance<KtExpression>().any { expression ->
    when (expression) {
      is KtBinaryExpression -> expression.operationReference.text == "version" &&
                               expression.right.contains(this) && expression.left.containsIntelliJPlatformPluginId()
      is KtDotQualifiedExpression -> {
        val versionCall = expression.selectorExpression as? KtCallExpression
        versionCall?.calleeExpression?.text == "version" &&
        versionCall.contains(this) && expression.receiverExpression.containsIntelliJPlatformPluginId()
      }
      else -> false
    }
  }
}

private fun PsiElement?.contains(element: PsiElement): Boolean = this != null && PsiTreeUtil.isAncestor(this, element, false)

private fun KtExpression?.containsIntelliJPlatformPluginId(): Boolean {
  return this?.descendantsOfType<KtStringTemplateExpression>()
    ?.any {
      IntelliJPlatformVersionCatalogUpdater.isPluginId(it.plainString()) &&
      it.parents(false).filterIsInstance<KtCallExpression>().firstOrNull()?.calleeExpression?.text == "id"
    } == true
}

private fun KtStringTemplateExpression.plainString(): String? {
  return entries.singleOrNull()?.text
}

@ApiStatus.Internal
interface IntelliJPlatformVersionCatalogUpdater {
  fun findReplacements(file: PsiFile, currentVersion: String, latestVersion: String): Map<PsiElement, String>

  companion object {
    const val PLUGIN_ID: String = "org.jetbrains.intellij.platform"

    fun isPluginId(pluginId: String?): Boolean = pluginId == PLUGIN_ID || pluginId?.startsWith("$PLUGIN_ID.") == true
  }
}

private const val INTELLIJ_PLATFORM_PLUGIN_ID = IntelliJPlatformVersionCatalogUpdater.PLUGIN_ID
