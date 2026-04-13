// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gitlab.ui.isGitlabCiFile
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue
import java.net.URI
import java.net.URISyntaxException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes


internal class GitLabCiIncludeApplicationMetricsCollector : ApplicationUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val result = performAnalysis()

    val metricEvents = result.includeStats.toMetricEvents().toMutableSet()

    metricEvents += ANALYZING_QUALITY_EVENT.metric(
      FILES_ANALYZED.with(result.filesAnalyzed),
      FILES_FAILED.with(result.filesFailed),
      TIMEOUT_HAPPENED.with(result.timeoutHappened)
    )

    return metricEvents
  }

  @VisibleForTesting
  internal suspend fun performAnalysis(): AnalysisResult {
    val state = AnalyzingState()

    val timedOut = withTimeoutOrNull(AnalyzingLimits.TIMEOUT) {
      performAnalyzingInternal(state)
    } == null

    return AnalysisResult(
      includeStats = state.includeStats,
      filesAnalyzed = state.filesAnalyzed,
      filesFailed = state.filesFailed,
      timeoutHappened = timedOut
    )
  }

  private class AnalyzingState {
    val includeStats = IncludeStats()
    var filesAnalyzed = 0
    var filesFailed = 0
  }

  private suspend fun performAnalyzingInternal(state: AnalyzingState) {
    val openProjects = readAction {
      ProjectManager.getInstance().openProjects.toList()
    }

    for (project in openProjects) {
      val gitlabCiFiles = collectGitLabCiFileForAnalyzing(project)

      for (file in gitlabCiFiles) {
        checkCanceled()
        try {
          val fileStats = analyzeFile(file, project)

          if (fileStats != null) {
            state.includeStats.addLogically(fileStats)
            state.filesAnalyzed++
          }
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Exception) {
          logger<GitLabCiIncludeApplicationMetricsCollector>().error(e)
          state.filesFailed++
        }
      }
    }
  }

  private suspend fun collectGitLabCiFileForAnalyzing(
    project: Project,
  ): List<VirtualFile> = readAction {
    if (project.isDisposed) return@readAction emptyList()

    val fileIndex = ProjectFileIndex.getInstance(project)
    val allFiles = mutableListOf<VirtualFile>()

    fileIndex.iterateContent { file ->
      if (file.isValid && !file.isDirectory && isGitlabCiFile(file)) {
        allFiles.add(file)
        if (allFiles.size >= AnalyzingLimits.MAX_FILES_TO_ANALYZE) {
          return@iterateContent false
        }
      }
      true
    }
    allFiles
  }

  private suspend fun analyzeFile(
    file: VirtualFile,
    project: Project,
  ): IncludeStats? = readAction {
    if (!file.isValid || project.isDisposed) return@readAction null

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@readAction null
    val yamlFile = psiFile as? YAMLFile ?: return@readAction null

    // Only process the first YAML document since GitLab CI/CD ignores subsequent documents
    val firstYamlDoc = yamlFile.documents.firstOrNull() ?: return@readAction IncludeStats()

    analyzeIncludesInDocument(firstYamlDoc)
  }

  private fun analyzeIncludesInDocument(yamlDoc: YAMLDocument): IncludeStats {
    val resultStats = IncludeStats()
    val topMapping = yamlDoc.topLevelValue as? YAMLMapping ?: return resultStats

    val includesValue = topMapping.keyValues.find {
      it.keyText == GitLabCiKeys.INCLUDE
    }?.value ?: return resultStats

    when (includesValue) {
      is YAMLScalar -> {
        analyzeIncludeEntry(includesValue, resultStats)
      }
      is YAMLMapping -> {
        analyzeIncludeEntry(includesValue, resultStats)
      }
      is YAMLSequence -> {
        includesValue.items
          .mapNotNull { it.value }
          .forEach { analyzeIncludeEntry(it, resultStats) }
      }
    }

    return resultStats
  }

  private fun analyzeIncludeEntry(yamlValue: YAMLValue, stats: IncludeStats) {
    when (yamlValue) {
      is YAMLScalar -> {
        val value = yamlValue.textValue
        val hasEnvVar = value.contains('$')

        if (guessIsGitLabCiRemoteUrl(value)) {
          stats.implicitLocalOrRemote.found = true
          if (hasEnvVar) stats.implicitLocalOrRemote.hasEnvVar = true
        }
        else {
          val hasSingleAsterisk = hasSingleAsteriskPattern(value)
          val hasDoubleAsterisk = value.contains("**")

          stats.implicitLocalOrRemote.found = true
          if (hasEnvVar) stats.implicitLocalOrRemote.hasEnvVar = true
          if (hasSingleAsterisk) stats.implicitLocalOrRemote.hasSingleAsterisk = true
          if (hasDoubleAsterisk) stats.implicitLocalOrRemote.hasDoubleAsterisk = true
        }
      }
      is YAMLMapping -> {
        val entries = yamlValue.keyValues
        val hasRules = entries.any { it.keyText == GitLabCiKeys.RULES }

        entries.find { it.keyText == GitLabCiKeys.LOCAL }?.let { localEntry ->
          stats.explicitLocal.found = true
          if (hasRules) stats.explicitLocal.hasRules = true

          val value = (localEntry.value as? YAMLScalar)?.textValue ?: return@let
          if (value.contains('$')) stats.explicitLocal.hasEnvVar = true
          if (hasSingleAsteriskPattern(value)) stats.explicitLocal.hasSingleAsterisk = true
          if (value.contains("**")) stats.explicitLocal.hasDoubleAsterisk = true
          return
        }

        entries.find { it.keyText == GitLabCiKeys.REMOTE }?.let { remoteEntry ->
          stats.explicitRemote.found = true
          if (hasRules) stats.explicitRemote.hasRules = true

          val value = (remoteEntry.value as? YAMLScalar)?.textValue ?: return@let
          if (value.contains('$')) stats.explicitRemote.hasEnvVar = true

          val hasCache = entries.any { it.keyText == GitLabCiKeys.CACHE }
          if (hasCache) stats.explicitRemote.hasCache = true
          return
        }

        entries.find { it.keyText == GitLabCiKeys.TEMPLATE }?.let { templateEntry ->
          stats.template.found = true
          if (hasRules) stats.template.hasRules = true

          val value = (templateEntry.value as? YAMLScalar)?.textValue ?: return@let
          if (value.contains('$')) stats.template.hasEnvVar = true
          return
        }

        entries.find { it.keyText == GitLabCiKeys.COMPONENT }?.let { componentEntry ->
          stats.component.found = true
          if (hasRules) stats.component.hasRules = true

          val value = (componentEntry.value as? YAMLScalar)?.textValue ?: return@let
          if (value.contains('$')) stats.component.hasEnvVar = true
          return
        }

        entries.find { it.keyText == GitLabCiKeys.PROJECT }?.let { projectEntry ->
          stats.project.found = true
          if (hasRules) stats.project.hasRules = true

          val hasRef = entries.any { it.keyText == GitLabCiKeys.REF }
          if (hasRef) stats.project.hasRef = true

          val projectValue = (projectEntry.value as? YAMLScalar)?.textValue ?: return@let
          if (projectValue.contains('$')) stats.project.hasEnvVar = true

          entries.find { it.keyText == GitLabCiKeys.FILE }?.let { fileEntry ->
            when (val fileValue = fileEntry.value) {
              is YAMLScalar -> {
                val filePath = fileValue.textValue
                if (hasSingleAsteriskPattern(filePath)) stats.project.hasSingleAsterisk = true
                if (filePath.contains("**")) stats.project.hasDoubleAsterisk = true
              }
              is YAMLSequence -> {
                fileValue.items.mapNotNull { it.value as? YAMLScalar }.forEach { scalar ->
                  val filePath = scalar.textValue
                  if (hasSingleAsteriskPattern(filePath)) stats.project.hasSingleAsterisk = true
                  if (filePath.contains("**")) stats.project.hasDoubleAsterisk = true
                }
              }
            }
          }
          return
        }

        stats.unknown = true
      }
      else -> {
        stats.unknown = true
      }
    }
  }

  @VisibleForTesting
  internal fun guessIsGitLabCiRemoteUrl(value: String): Boolean {
    return try {
      val uri = URI(value)
      uri.scheme in setOf("http", "https")
    }
    catch (_: URISyntaxException) {
      false
    }
  }

  private fun hasSingleAsteriskPattern(value: String): Boolean {
    val withoutDoubleAsterisks = value.replace("**", "")
    return withoutDoubleAsterisks.contains('*')
  }
}

@VisibleForTesting
internal class IncludeTypeStats(
  var found: Boolean = false,
  var hasRules: Boolean = false,
  var hasEnvVar: Boolean = false,
  var hasSingleAsterisk: Boolean = false,
  var hasDoubleAsterisk: Boolean = false,
  var hasCache: Boolean = false,
  var hasRef: Boolean = false,
) {
  fun merge(other: IncludeTypeStats) {
    found = found || other.found
    hasRules = hasRules || other.hasRules
    hasEnvVar = hasEnvVar || other.hasEnvVar
    hasSingleAsterisk = hasSingleAsterisk || other.hasSingleAsterisk
    hasDoubleAsterisk = hasDoubleAsterisk || other.hasDoubleAsterisk
    hasCache = hasCache || other.hasCache
    hasRef = hasRef || other.hasRef
  }
}

@VisibleForTesting
internal class IncludeStats(
  val explicitLocal: IncludeTypeStats = IncludeTypeStats(),
  val explicitRemote: IncludeTypeStats = IncludeTypeStats(),
  val implicitLocalOrRemote: IncludeTypeStats = IncludeTypeStats(),
  val template: IncludeTypeStats = IncludeTypeStats(),
  val component: IncludeTypeStats = IncludeTypeStats(),
  val project: IncludeTypeStats = IncludeTypeStats(),
  var unknown: Boolean = false,
) {
  fun addLogically(other: IncludeStats) {
    explicitLocal.merge(other.explicitLocal)
    explicitRemote.merge(other.explicitRemote)
    implicitLocalOrRemote.merge(other.implicitLocalOrRemote)
    template.merge(other.template)
    component.merge(other.component)
    project.merge(other.project)
    unknown = unknown || other.unknown
  }

  fun toMetricEvents(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    if (explicitLocal.found) {
      result += EXPLICIT_LOCAL_TYPE_EVENT.metric(
        WITH_RULES.with(explicitLocal.hasRules),
        WITH_ENV_VAR.with(explicitLocal.hasEnvVar),
        WITH_SINGLE_ASTERISK.with(explicitLocal.hasSingleAsterisk),
        WITH_DOUBLE_ASTERISK.with(explicitLocal.hasDoubleAsterisk),
      )
    }

    if (explicitRemote.found) {
      result += EXPLICIT_REMOTE_TYPE_EVENT.metric(
        WITH_RULES.with(explicitRemote.hasRules),
        WITH_ENV_VAR.with(explicitRemote.hasEnvVar),
        WITH_CACHE.with(explicitRemote.hasCache),
      )
    }

    if (implicitLocalOrRemote.found) {
      result += IMPLICIT_LOCAL_OR_REMOTE_TYPE_EVENT.metric(
        WITH_ENV_VAR.with(implicitLocalOrRemote.hasEnvVar),
        WITH_SINGLE_ASTERISK.with(implicitLocalOrRemote.hasSingleAsterisk),
        WITH_DOUBLE_ASTERISK.with(implicitLocalOrRemote.hasDoubleAsterisk),
      )
    }

    if (template.found) {
      result += TEMPLATE_TYPE_EVENT.metric(
        WITH_RULES.with(template.hasRules),
        WITH_ENV_VAR.with(template.hasEnvVar),
      )
    }

    if (component.found) {
      result += COMPONENT_TYPE_EVENT.metric(
        WITH_RULES.with(component.hasRules),
        WITH_ENV_VAR.with(component.hasEnvVar),
      )
    }

    if (project.found) {
      result += PROJECT_TYPE_EVENT.metric(
        WITH_RULES.with(project.hasRules),
        WITH_ENV_VAR.with(project.hasEnvVar),
        WITH_REF.with(project.hasRef),
        WITH_SINGLE_ASTERISK.with(project.hasSingleAsterisk),
        WITH_DOUBLE_ASTERISK.with(project.hasDoubleAsterisk),
      )
    }

    if (unknown) {
      result += UNKNOWN_TYPE_EVENT.metric()
    }

    return result
  }
}

@VisibleForTesting
internal data class AnalysisResult(
  val includeStats: IncludeStats,
  val filesAnalyzed: Int,
  val filesFailed: Int,
  val timeoutHappened: Boolean,
)

private object GitLabCiKeys {
  const val INCLUDE = "include"
  const val LOCAL = "local"
  const val REMOTE = "remote"
  const val RULES = "rules"
  const val CACHE = "cache"
  const val REF = "ref"
  const val FILE = "file"
  const val TEMPLATE = "template"
  const val COMPONENT = "component"
  const val PROJECT = "project"
}

private object AnalyzingLimits {
  const val MAX_FILES_TO_ANALYZE = 100
  val TIMEOUT = 5.minutes
}

private val WITH_RULES = EventFields.Boolean("with_rules", "is `rules:` section present")
private val WITH_ENV_VAR = EventFields.Boolean("with_env_var", "are any environment variables used in the main file path / URL")
private val WITH_SINGLE_ASTERISK = EventFields.Boolean("with_single_asterisk", "are any `*` used in file(-s) path(-s)")
private val WITH_DOUBLE_ASTERISK = EventFields.Boolean("with_double_asterisk", "are any `**` used in file(-s) path(-s)")
private val WITH_CACHE = EventFields.Boolean("with_cache", "is `cache:` directive present")
private val WITH_REF = EventFields.Boolean("with_ref", "is `ref:` key-value present")

private val FILES_ANALYZED = EventFields.RoundedInt("files_analyzed", "Number of GitLab CI files successfully analyzed")
private val FILES_FAILED = EventFields.RoundedInt("files_failed", "Number of GitLab CI files that failed to analyze")
private val TIMEOUT_HAPPENED = EventFields.Boolean("timeout_happened", "Whether the analyzing timed out before completion")

private val GROUP = EventLogGroup(
  id = "gitlab.ci.include",
  version = 2
)

private val EXPLICIT_LOCAL_TYPE_EVENT = GROUP.registerVarargEvent(
  "explicit.local",
  WITH_RULES, WITH_ENV_VAR,
  WITH_SINGLE_ASTERISK, WITH_DOUBLE_ASTERISK,
)

private val EXPLICIT_REMOTE_TYPE_EVENT = GROUP.registerVarargEvent(
  "explicit.remote",
  WITH_RULES, WITH_ENV_VAR,
  WITH_CACHE
)

private val IMPLICIT_LOCAL_OR_REMOTE_TYPE_EVENT = GROUP.registerVarargEvent(
  "implicit.local.or.remote",
  WITH_ENV_VAR,
  WITH_SINGLE_ASTERISK, WITH_DOUBLE_ASTERISK,
)

private val TEMPLATE_TYPE_EVENT = GROUP.registerVarargEvent(
  "template",
  WITH_RULES, WITH_ENV_VAR,
)

private val COMPONENT_TYPE_EVENT = GROUP.registerVarargEvent(
  "component",
  WITH_RULES, WITH_ENV_VAR,
)

private val PROJECT_TYPE_EVENT = GROUP.registerVarargEvent(
  "project",
  WITH_RULES, WITH_ENV_VAR,
  WITH_REF,
  WITH_SINGLE_ASTERISK,  // no usages expected
  WITH_DOUBLE_ASTERISK,  // no usages expected
)

private val UNKNOWN_TYPE_EVENT = GROUP.registerEvent(
  "unknown"
)

private val ANALYZING_QUALITY_EVENT = GROUP.registerVarargEvent(
  "analyzing.quality",
  FILES_ANALYZED,
  FILES_FAILED,
  TIMEOUT_HAPPENED
)