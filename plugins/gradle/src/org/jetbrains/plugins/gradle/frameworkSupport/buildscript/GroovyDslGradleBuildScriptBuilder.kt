// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class GroovyDslGradleBuildScriptBuilder<Self : GroovyDslGradleBuildScriptBuilder<Self>>(
  gradleVersion: GradleVersion,
) : AbstractGradleBuildScriptBuilder<Self>(gradleVersion) {

  override fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit): Self =
    withPostfix {
      callIfNotEmpty("test", configure)
    }

  override fun withKotlinJvmPlugin(version: String?): Self = apply {
    withMavenCentral()
    withPlugin("org.jetbrains.kotlin.jvm", version)
  }

  override fun withKotlinTest(): Self = apply {
    withMavenCentral()
    // The kotlin-test dependency version is inherited from the Kotlin plugin
    addTestImplementationDependency("org.jetbrains.kotlin:kotlin-test")
    configureTestTask {
      call("useJUnitPlatform")
    }
  }

  override fun ScriptTreeBuilder.mavenRepository(url: String): ScriptTreeBuilder = applyKt {
    call("maven") {
      assign("url", url)
    }
  }

  override fun generate(): String {
    return GroovyScriptBuilder().generate(generateTree())
  }

  internal class Impl(gradleVersion: GradleVersion) : GroovyDslGradleBuildScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}