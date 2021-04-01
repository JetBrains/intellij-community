// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import kotlin.apply as applyKt

class GroovyGradleBuildScriptBuilder(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<GroovyScriptBuilder, GroovyGradleBuildScriptBuilder>(gradleVersion) {

  override fun apply(action: GroovyGradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  override fun createScriptBuilder() = GroovyScriptBuilder()

  override fun addPlugin(id: String, version: String?) = withPlugin {
    when (version) {
      null -> code("id $id")
      else -> code("id $id version $version")
    }
  }
}