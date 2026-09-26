// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.otherIde

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
internal class DotVSCodeProjectCollectorTest {
  companion object {
    private const val dotVsCode = ".vscode"
  }

  private val projectDirFixture = tempPathFixture()
  private val project by projectFixture(projectDirFixture)
  private val projectDir by projectDirFixture

  @Test
  fun `test no vscode detected`() = timeoutRunBlocking {
    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(1)
    assertThat(events.first().data.build()).containsEntry("exists", false)

    Unit
  }

  @Test
  fun `test normal detection`() = timeoutRunBlocking {
    Files.createDirectory(projectDir.resolve(dotVsCode))

    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(1)
    assertThat(events.first().data.build()).containsEntry("exists", true)

    Unit
  }

  @Test
  fun `test cpp settings detection`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val settingsJson = vscDirectory.resolve("settings.json")
    Files.createFile(settingsJson)
    Files.writeString(settingsJson, """
      {
        "C_Cpp.default.systemIncludePath": []
      }
    """.trimIndent())


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(2)
    assertThat(events.first().data.build()).containsEntry("exists", true)
    val cppEvent = events.last().data.build()
    assertThat(cppEvent).containsEntry("has_cpp_properties", false)
    assertThat(cppEvent).containsEntry("has_cpp_settings", true)

    Unit
  }

  @Test
  fun `test cpp settings malformed input does not cause issues`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val settingsJson = vscDirectory.resolve("settings.json")
    Files.createFile(settingsJson)
    Files.writeString(settingsJson, """
      {
        testMe: {
      }
    """.trimIndent())


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(1)
    assertThat(events.first().data.build()).containsEntry("exists", true)
    Unit
  }

  @Test
  fun `test cpp properties detection`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val cCppPropsJson = vscDirectory.resolve("c_cpp_properties.json")
    Files.createFile(cCppPropsJson)
    Files.writeString(cCppPropsJson, """
      {
        "version": 4
      }
    """.trimIndent())


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(2)
    assertThat(events.first().data.build()).containsEntry("exists", true)
    val cppEvent = events.last().data.build()
    assertThat(cppEvent).containsEntry("has_cpp_properties", true)
    assertThat(cppEvent).containsEntry("has_cpp_settings", false)

    Unit
  }

  @Test
  fun `test cpp properties malformed input does not cause issues`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val cCppPropsJson = vscDirectory.resolve("c_cpp_properties.json")
    Files.createFile(cCppPropsJson)
    Files.writeString(cCppPropsJson, """
      {
        test me: {
      }
    """.trimIndent())


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events.first().data.build()).containsEntry("exists", true)
    val cppEvent = events.last().data.build()
    assertThat(cppEvent).containsEntry("has_cpp_properties", true)

    Unit
  }

  @Test
  fun `test cpp settings malformed input does not cause issues but cpp props are reported`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val settingsJson = vscDirectory.resolve("settings.json")
    Files.createFile(settingsJson)
    Files.writeString(settingsJson, """
      {
        testMe: {
      }
    """.trimIndent())

    val cCppPropsJson = vscDirectory.resolve("c_cpp_properties.json")
    Files.createFile(cCppPropsJson)
    Files.writeString(cCppPropsJson, """
      {
        "version": 4
      }
    """.trimIndent())


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(2)
    assertThat(events.first().data.build()).containsEntry("exists", true)

    val cppEvent = events.last().data.build()
    assertThat(cppEvent).containsEntry("has_cpp_properties", true)
    assertThat(cppEvent).containsEntry("has_cpp_settings", false)
    Unit
  }

  @Test
  fun `test launch json is detected`() = timeoutRunBlocking {
    val vscDirectory = projectDir.resolve(dotVsCode)
    Files.createDirectory(vscDirectory)
    val launchJson = vscDirectory.resolve("launch.json")
    Files.createFile(launchJson)
    Files.writeString(launchJson, "{}")


    val collector = DotVSCodeProjectCollector()
    val events = collector.getMetrics(project)
    assertThat(events).hasSize(2)
    assertThat(events.first().data.build()).containsEntry("exists", true)

    val launchJsonEvent = events.last().data.build()
    assertThat(launchJsonEvent).containsEntry("hasCompoundConfigurations", false)
    assertThat(launchJsonEvent).containsEntry("numberOfConfigurations", 0)
    Unit
  }
}
