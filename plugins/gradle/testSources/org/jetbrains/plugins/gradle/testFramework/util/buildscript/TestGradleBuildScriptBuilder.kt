// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder

interface TestGradleBuildScriptBuilder<BSB : TestGradleBuildScriptBuilder<BSB>> : GradleBuildScriptBuilder<BSB> {

  companion object {

    @JvmStatic
    fun create(gradleVersion: GradleVersion, useKotlinDsl: Boolean): TestGradleBuildScriptBuilder<*> {
      return when (useKotlinDsl) {
        true -> TestKotlinDslGradleBuildScriptBuilder.Impl(gradleVersion)
        else -> TestGroovyDslGradleBuildScriptBuilder.Impl(gradleVersion)
      }
    }
  }
}