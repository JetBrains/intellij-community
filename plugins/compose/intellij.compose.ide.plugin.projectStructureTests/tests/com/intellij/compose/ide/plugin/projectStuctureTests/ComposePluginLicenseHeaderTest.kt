// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.projectStuctureTests

import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration.Companion.loadIntelliJProject
import org.jetbrains.jps.model.JpsProject
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertTrue

class ComposePluginLicenseHeaderTest {

  @Test
  fun `collects some Kotlin source files`() = withComposeIdePluginKotlinFiles { srcFiles ->
    assertTrue(srcFiles.isNotEmpty())
    assertTrue(srcFiles.all { it.extension == "kt" })
  }

  @Test
  fun `all files reference Apache license header`() = withComposeIdePluginKotlinFiles { srcFiles ->
    val filesWithMissingHeaderPart = srcFiles.filter { file ->
      val hasApacheMentionLine = file.useLines { lines ->
        lines.any { it.contains(FULL_APACHE_REFERENCE) || it.contains(SHORT_APACHE_REFERENCE) }
      }
      !hasApacheMentionLine
    }

    assertTrue(filesWithMissingHeaderPart.isEmpty(), "The following files don't Apache license header:\n${filesWithMissingHeaderPart.joinToString("\n") { it.absolutePathString() }}")
  }

  @Test
  fun `all files reference JetBrains as contributor`() = withComposeIdePluginKotlinFiles { srcFiles ->
    val filesWithMissingHeaderPart = srcFiles.filter { file ->
      val referenceJetBrainsAsContributor = file.useLines { lines ->
        lines.any { it.contains(JETBRAINS_COPYRIGHT_REGEX) }
      }
      !referenceJetBrainsAsContributor
    }

    assertTrue(filesWithMissingHeaderPart.isEmpty(), "The following files don't mention JetBrains as file contributor:\n${filesWithMissingHeaderPart.joinToString("\n") { it.absolutePathString() }}")
  }

  @Test
  fun `all files that reference AOSP as source also reference JetBrains as modifier and second contributor of the file`() = withComposeIdePluginKotlinFiles { srcFiles ->
    val filesMentioningAOSPUsingInconsistentYears = srcFiles.filter { file ->
      val aospCopyright = file.useLines { lines ->
        lines.firstNotNullOfOrNull { AOSP_COPYRIGHT_REGEX.find(it) }
      }
      val jetbrainsCopyright = file.useLines { lines ->
        lines.firstNotNullOfOrNull { JETBRAINS_COPYRIGHT_REGEX.find(it) }
      }
      val jetbrainsModified = file.useLines { lines ->
        lines.firstNotNullOfOrNull { JETBRAINS_MODIFIED_REGEX.find(it) }
      }
      val notMentionAOSPOrUsesUpdatedYearInContextOfJetBrains = aospCopyright == null || (
        jetbrainsCopyright != null && aospCopyright.groups["year"]!!.value.toInt() <= jetbrainsCopyright.groups["year"]!!.value.toInt() &&
        jetbrainsModified != null && aospCopyright.groups["year"]!!.value.toInt() <= jetbrainsModified.groups["year"]!!.value.toInt())
      !notMentionAOSPOrUsesUpdatedYearInContextOfJetBrains
    }

    assertTrue(filesMentioningAOSPUsingInconsistentYears.isEmpty(), "The following files uses wrong years referenced when referenceing AOSP sources:${filesMentioningAOSPUsingInconsistentYears.joinToString(separator = "\n", prefix = "\n") { it.absolutePathString() }}")
  }

  private fun withComposeIdePluginKotlinFiles(f: (List<Path>) -> Unit) {
    val modules = ultimateProject.modules.filter { it.name.startsWith(COMPOSE_PLUGIN_MODULE_NAME_PREFIX) }
    val sourceFiles = buildList {
      for (module in modules) for (sourceRoot in module.sourceRoots) {
        sourceRoot.path.walk().filter { it.extension == "kt" }.forEach { add(it) }
      }
    }
    f(sourceFiles)
  }

  companion object {
    private val ultimateRoot: Path by lazy {
      Path.of(PathManager.getHomePath())
    }

    private val ultimateProject: JpsProject by lazy {
      loadIntelliJProject(ultimateRoot.pathString)
    }

    private const val COMPOSE_PLUGIN_MODULE_NAME_PREFIX: String = "intellij.compose.ide.plugin"

    private const val FULL_APACHE_REFERENCE: String = "Licensed under the Apache License, Version 2.0 (the \"License\")"

    private const val SHORT_APACHE_REFERENCE: String = "Use of this source code is governed by the Apache 2.0 license"

    private val JETBRAINS_COPYRIGHT_REGEX: Regex = Regex("""Copyright( \(C\))? (\d+-)?(?<year>\d+) JetBrains s\.r\.o\.""")

    private val JETBRAINS_MODIFIED_REGEX: Regex = Regex("""Modified (?<year>\d+) by JetBrains s\.r\.o\.""")

    private val AOSP_COPYRIGHT_REGEX: Regex = Regex("""Copyright \(C\) (?<year>\d+) The Android Open Source Project""")
  }
}