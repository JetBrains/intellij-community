// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.builders

import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder

class PluginGradleTestFixtureBuilder(private val pluginName: String) : SingleGradleBuildFileFixtureBuilder("$pluginName-plugin-project") {

  override fun configureBuildFile(builder: TestGradleBuildScriptBuilder) {
    with(builder) {
      when (pluginName) {
        "java" -> withJavaPlugin()
        "idea" -> withIdeaPlugin()
        "groovy" -> withGroovyPlugin()
        else -> withPlugin(pluginName)
      }
    }
  }

  companion object {
    val JAVA_PROJECT: GradleTestFixtureBuilder = PluginGradleTestFixtureBuilder("java")
    val GROOVY_PROJECT: GradleTestFixtureBuilder = PluginGradleTestFixtureBuilder("groovy")
    val IDEA_PLUGIN_PROJECT: GradleTestFixtureBuilder = PluginGradleTestFixtureBuilder("idea")
  }
}