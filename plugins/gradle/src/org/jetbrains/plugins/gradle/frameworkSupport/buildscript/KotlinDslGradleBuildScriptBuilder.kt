// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
class KotlinDslGradleBuildScriptBuilder(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<KotlinDslGradleBuildScriptBuilder>(gradleVersion) {

  override fun apply(action: KotlinDslGradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  override fun generate() = KotlinScriptBuilder().generate(generateTree())

  override fun withKotlinJvmPlugin(version: String?): KotlinDslGradleBuildScriptBuilder = apply {
    withMavenCentral()
    withPlugin {
      if (version != null) {
        infixCall(call("kotlin", "jvm"), "version", string(version))
      } else {
        call("kotlin", "jvm")
      }
    }
  }

  override fun withKotlinTest() = apply {
    withMavenCentral()
    addTestImplementationDependency(call("kotlin", "test"))
    configureTestTask {
      call("useJUnitPlatform")
    }
  }

  override fun configureTestTask(configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix {
      callIfNotEmpty("tasks.test", configure)
    }
}