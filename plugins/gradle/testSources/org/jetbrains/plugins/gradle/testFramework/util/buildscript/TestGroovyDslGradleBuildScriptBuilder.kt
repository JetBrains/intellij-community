// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.buildscript

import com.intellij.testFramework.UsefulTestCase
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GroovyDslGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class TestGroovyDslGradleBuildScriptBuilder<BSB : TestGroovyDslGradleBuildScriptBuilder<BSB>>(
  gradleVersion: GradleVersion
) : GroovyDslGradleBuildScriptBuilder<BSB>(gradleVersion),
    TestGradleBuildScriptBuilder<BSB> {

  override fun ScriptTreeBuilder.mavenCentral(): ScriptTreeBuilder = applyKt {
    when {
      UsefulTestCase.IS_UNDER_TEAMCITY -> {
        mavenRepository("https://repo.labs.intellij.net/repo1")
      }
      else -> {
        // IntelliJ internal maven repo is not available in local environment
        call("mavenCentral")
      }
    }
  }

  internal class Impl(gradleVersion: GradleVersion) : TestGroovyDslGradleBuildScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}