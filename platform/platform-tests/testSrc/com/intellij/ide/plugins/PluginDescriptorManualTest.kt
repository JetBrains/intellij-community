// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.jupiter.api.Disabled
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

@Disabled // manual run
class PluginDescriptorManualTest {
  @Test
  fun testProduction2() {
    IoTestUtil.assumeMacOS()

    assumeNotUnderTeamcity()
    val path = Paths.get("/Volumes/data/plugins")
    assumeTrue(path.exists())
    val descriptors = PluginSetTestBuilder(path = path)
      .build()
      .allPlugins
    assertThat(descriptors).isNotEmpty()
  }

  @Test
  fun testProductionPlugins() {
    IoTestUtil.assumeMacOS()
    assumeNotUnderTeamcity()
    val path = Paths.get("/Applications/Idea.app/Contents/plugins")
    assumeTrue(path.exists())
    val descriptors = PluginSetTestBuilder(path = path)
      .build()
      .allPlugins
    assertThat(descriptors).isNotEmpty()
    assertThat(descriptors.find { it.pluginId.idString == "com.intellij.java" }).isNotNull
  }

  @Test
  fun testProductionProductLib() {
    IoTestUtil.assumeMacOS()
    assumeNotUnderTeamcity()
    val dir = Path.of("/Applications/Idea.app/Contents/lib")
    assumeTrue(Files.exists(dir))

    val urls = Files.newDirectoryStream(dir).use { stream ->
      stream.map { it.toUri().toURL() }
    }
    val descriptors = testLoadAndInitDescriptorsFromClassPath(URLClassLoader(urls.toTypedArray(), null))
    // core and com.intellij.workspace
    Assertions.assertThat(descriptors).hasSize(1)
  }

  companion object {
    private fun assumeNotUnderTeamcity() {
      assumeTrue("Must not be run under TeamCity", !UsefulTestCase.IS_UNDER_TEAMCITY)
    }
  }
}