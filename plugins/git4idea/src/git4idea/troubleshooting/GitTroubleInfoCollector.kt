// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.troubleshooting

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettingType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsSharedProjectSettings
import com.intellij.troubleshooting.TroubleInfoCollector
import git4idea.config.GitConfigUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVcsApplicationSettings
import git4idea.repo.GitRepositoryManager

private val LOG = logger<GitTroubleInfoCollector>()

internal class GitTroubleInfoCollector : TroubleInfoCollector, PluginAware {
  private lateinit var pluginDescriptor: PluginDescriptor

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  override fun collectInfo(project: Project): String = buildString {
    writePathAndVersion(project)
    writeSettings()
    writeGitConfig(project)
    writeModifiedGitAdvancedSettings()
    writeMappings(project)
    writeGitReposStats(project)
  }

  override fun toString(): String = "Git"

  private fun StringBuilder.writePathAndVersion(project: Project) {
    val executableManager = GitExecutableManager.getInstance()
    val pathToGit = executableManager.getPathToGit(project)
    val version = executableManager.getVersion(project)
    section("Git executable") {
      appendLine("Path: $pathToGit")
      appendLine("Version: $version")
    }
  }

  private fun StringBuilder.writeMappings(project: Project) {
    val mappingAutoDetection = VcsSharedProjectSettings.getInstance(project).isDetectVcsMappingsAutomatically
    val mappings = ProjectLevelVcsManager.getInstance(project).directoryMappings.map {
      "${it.vcs.ifEmpty { "No VCS" }} -> ${it.directory.ifEmpty { "<default>" }}"
    }

    section("VCS mappings") {
      appendLine("Auto-detection: $mappingAutoDetection")
      appendLine("Mappings (total ${mappings.size}):")
      appendLine(mappings.joinToString(separator = "\n") { "  $it" })
    }
  }

  private fun StringBuilder.writeModifiedGitAdvancedSettings() {
    val customAdvancedSettings = mutableListOf<String>()
    AdvancedSettingBean.EP_NAME.processWithPluginDescriptor { bean, descriptor ->
      if (pluginDescriptor == descriptor) {
        val value = when (bean.type()) {
          AdvancedSettingType.Int -> AdvancedSettings.getInt(bean.id)
          AdvancedSettingType.Bool -> AdvancedSettings.getBoolean(bean.id)
          AdvancedSettingType.String -> AdvancedSettings.getString(bean.id)
          AdvancedSettingType.Enum -> {
            val enumClass = runCatching {
              @Suppress("UNCHECKED_CAST")
              descriptor.classLoader.loadClass(bean.enumClass) as? Class<out Enum<*>>
            }.getOrNull() ?: return@processWithPluginDescriptor
            AdvancedSettings.getEnum(bean.id, enumClass)
          }
        }
        if (value != bean.defaultValueObject) {
          customAdvancedSettings.add("${bean.id}=${value}")
        }
      }
    }

    if (customAdvancedSettings.isNotEmpty()) {
      section("Advanced settings") {
        appendLine(customAdvancedSettings.joinToString(separator = "\n") { "  $it" })
      }
    }
  }

  private fun StringBuilder.writeSettings() {
    val applicationSettings = GitVcsApplicationSettings.getInstance()
    section("Settings") {
      appendLine("Credential helper: ${applicationSettings.isUseCredentialHelper}")
    }
  }

  private fun StringBuilder.writeGitConfig(project: Project) {
    val config = GitConfigHelper.readGitConfig(project) ?: return
    section("Git config") {
      GitConfigHelper.filter(config).forEach { (k, v) ->
        appendLine("  $k=$v")
      }
    }
  }

  private fun StringBuilder.section(name: String, block: StringBuilder.() -> Unit) {
    appendLine("==== $name ====")
    block()
    appendLine("\n")
  }

  private fun StringBuilder.writeGitReposStats(project: Project) {
    section("Git repositories stats") {
      GitRepositoryManager.getInstance(project).repositories.forEach {
        val tags = it.tagHolder.getTags().size
        val remoteBranches = it.branches.remoteBranches.size
        val localBranches = it.branches.localBranches.size

        appendLine("${it.root.name}: $localBranches branches, $remoteBranches remote branches, $tags tags")
      }
    }
  }
}

private object GitConfigHelper {
  const val MASKED_VALUE = "***"

  fun readGitConfig(project: Project): Map<String, String>? = try {
    val singleGitRoot = GitRepositoryManager.getInstance(project).repositories.singleOrNull()
    if (singleGitRoot != null) {
      GitConfigUtil.getValues(project, singleGitRoot.root, null)
    }
    else {
      val projectDir = project.guessProjectDir() ?: return null
      GitConfigUtil.getValues(project, projectDir, null)
    }
  }
  catch (e: VcsException) {
    LOG.warn("Failed to read git config", e)
    null
  }

  fun filter(config: Map<String, String>) = config.mapNotNull { (key, value) ->
    val inclusion = when {
      key.startsWith("core.")
      || key.startsWith("i18n.")
      || key.startsWith("rebase.")
      || key.startsWith("merge.")
      || key == GitConfigUtil.GPG_COMMIT_SIGN ->
        Inclusion.INCLUDE

      key in setOf(GitConfigUtil.GPG_COMMIT_SIGN_KEY,
                   GitConfigUtil.COMMIT_TEMPLATE,
                   GitConfigUtil.GPG_PROGRAM,
                   GitConfigUtil.CREDENTIAL_HELPER) ->
        Inclusion.INCLUDE_MASKED

      else -> Inclusion.SKIP
    }

    when (inclusion) {
      Inclusion.INCLUDE -> key to value
      Inclusion.INCLUDE_MASKED -> key to MASKED_VALUE
      Inclusion.SKIP -> null
    }
  }.sortedBy { (key, _) -> key }.toMap()

  enum class Inclusion {
    INCLUDE,
    INCLUDE_MASKED,
    SKIP,
  }
}