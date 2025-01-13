// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class GroovyDslGradleBuildScriptBuilder<BSB : GroovyDslGradleBuildScriptBuilder<BSB>>(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<BSB>(gradleVersion) {

  override fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix {
      callIfNotEmpty("test", configure)
    }

  override fun withKotlinJvmPlugin(version: String?) = apply {
    withMavenCentral()
    withPlugin("org.jetbrains.kotlin.jvm", version)
  }

  override fun withKotlinTest() = apply {
    withMavenCentral()
    // version is inherited from the Kotlin plugin
    addTestImplementationDependency("org.jetbrains.kotlin:kotlin-test")
    configureTestTask {
      call("useJUnitPlatform")
    }
  }

  override fun ScriptTreeBuilder.mavenRepository(url: String) = applyKt {
    call("maven") {
      assign("url", url)
    }
  }

  override fun generate() = GroovyScriptBuilder().generate(generateTree())

  internal class Impl(gradleVersion: GradleVersion) : GroovyDslGradleBuildScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}