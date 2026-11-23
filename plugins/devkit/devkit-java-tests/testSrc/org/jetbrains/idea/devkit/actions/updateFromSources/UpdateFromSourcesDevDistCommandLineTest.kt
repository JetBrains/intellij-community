// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.updateFromSources

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.pathString

internal class UpdateFromSourcesDevDistCommandLineTest {
  @TempDir
  lateinit var tempDir: Path

  private fun devIdeaHome(): Path {
    val home = tempDir.resolve("idea")
    home.resolve("build").createDirectories().resolve("dist.cmd").createFile()
    return home
  }

  @Test
  fun `the command stages the update and names the enabled plugins`() {
    val devIdeaHome = devIdeaHome()
    val workIdeHome = tempDir.resolve("IDEA.app/Contents")
    val resultFile = devIdeaHome.resolve("out/update-from-sources/result.json")
    val logFile = devIdeaHome.resolve("out/update-from-sources/update.log")

    val commandLine = createDevDistUpdateCommandLine(
      devIdeaHome = devIdeaHome,
      workIdeHome = workIdeHome,
      pluginDirs = listOf("fullLine", "my plugin"),
      allPlugins = false,
      resultFile = resultFile,
      logFile = logFile,
    )

    assertThat(commandLine.exePath).isEqualTo(devIdeaHome.resolve("build/dist.cmd").pathString)
    assertThat(commandLine.workingDirectory).isEqualTo(devIdeaHome)
    assertThat(commandLine.parametersList.list).containsExactly(
      "update-ide",
      "--home=${workIdeHome.pathString}",
      "--plugin=fullLine",
      "--plugin=my plugin",
      "--stage-only",
      "--result=${resultFile.pathString}",
      "--log=${logFile.pathString}",
    )
  }

  @Test
  fun `all plugins replace the plugin directories`() {
    val devIdeaHome = devIdeaHome()
    val workIdeHome = tempDir.resolve("work")
    val resultFile = devIdeaHome.resolve("out/update-from-sources/result.json")
    val logFile = devIdeaHome.resolve("out/update-from-sources/update.log")

    val commandLine = createDevDistUpdateCommandLine(
      devIdeaHome = devIdeaHome,
      workIdeHome = workIdeHome,
      pluginDirs = emptyList(),
      allPlugins = true,
      resultFile = resultFile,
      logFile = logFile,
    )

    assertThat(commandLine.parametersList.list).containsExactly(
      "update-ide",
      "--home=${workIdeHome.pathString}",
      "--all-plugins",
      "--stage-only",
      "--result=${resultFile.pathString}",
      "--log=${logFile.pathString}",
    )
  }

  @Test
  fun `each path option is absolute and normalized`() {
    val devIdeaHome = devIdeaHome()
    val commandLine = createDevDistUpdateCommandLine(
      devIdeaHome = devIdeaHome,
      workIdeHome = tempDir.resolve("a/../work/."),
      pluginDirs = emptyList(),
      allPlugins = false,
      resultFile = Path.of("out/result.json"),
      logFile = devIdeaHome.resolve("out/./update.log"),
    )

    assertThat(commandLine.parametersList.list).containsExactly(
      "update-ide",
      "--home=${tempDir.resolve("work").pathString}",
      "--stage-only",
      "--result=${Path.of("out/result.json").toAbsolutePath().pathString}",
      "--log=${devIdeaHome.resolve("out/update.log").pathString}",
    )
  }

  @Test
  fun `a checkout without the dev dist script is refused`() {
    assertThatThrownBy {
      createDevDistUpdateCommandLine(tempDir, tempDir.resolve("work"), emptyList(), false, tempDir.resolve("result.json"), tempDir.resolve("update.log"))
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `all plugins together with plugin directories are refused`() {
    assertThatThrownBy {
      createDevDistUpdateCommandLine(devIdeaHome(), tempDir.resolve("work"), listOf("fullLine"), true, tempDir.resolve("result.json"), tempDir.resolve("update.log"))
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `the result gives the apply command, the repository and the skipped plugins`() {
    val result = parseDevDistUpdateResult("""
      {
        "version": 1,
        "apply": ["/tmp/IDEA.app.dist", "update-ide", "--apply-staged", "--home=/tmp/IDEA.app/Contents"],
        "repository": "/idea/out/update-from-sources/plugins/plugins.xml",
        "skippedPlugins": ["unknown"],
        "fingerprint": "v5:abc",
        "futureField": true
      }
    """.trimIndent())

    assertThat(result.apply).containsExactly("/tmp/IDEA.app.dist", "update-ide", "--apply-staged", "--home=/tmp/IDEA.app/Contents")
    assertThat(result.repository).isEqualTo("/idea/out/update-from-sources/plugins/plugins.xml")
    assertThat(result.skippedPlugins).containsExactly("unknown")
    assertThat(result.fingerprint).isEqualTo("v5:abc")
  }

  @Test
  fun `a result without a repository has a null repository and no skipped plugins`() {
    val result = parseDevDistUpdateResult("""{"version":1,"apply":["x"],"repository":null,"skippedPlugins":[],"fingerprint":"v5:abc"}""")

    assertThat(result.repository).isNull()
    assertThat(result.skippedPlugins).isEmpty()
  }

  @Test
  fun `a result of another version or without an apply command is refused`() {
    assertThatThrownBy { parseDevDistUpdateResult("""{"version":2,"apply":["x"]}""") }
      .isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy { parseDevDistUpdateResult("""{"version":1,"apply":[]}""") }
      .isInstanceOf(IllegalArgumentException::class.java)
  }
}
