// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.tree
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class KotlinDslGradleBuildScriptBuilder<Self : KotlinDslGradleBuildScriptBuilder<Self>>(
  gradleVersion: GradleVersion,
) : AbstractGradleBuildScriptBuilder<Self>(gradleVersion) {

  private val PREDEFINED_TASKS = setOf("test", "compileJava", "compileTestJava")

  override fun configureTask(name: String, type: String, configure: ScriptTreeBuilder.() -> Unit): Self =
    withPostfix {
      val block = tree(configure)
      if (!block.isEmpty()) {
        if (name in PREDEFINED_TASKS) {
          call("tasks.$name", argument(block))
        }
        else {
          call("tasks.named<$type>", argument(name), argument(block))
        }
      }
    }

  override fun withKotlinJvmPlugin(version: String?): Self = apply {
    withMavenCentral()
    withPlugin {
      if (version != null) {
        infixCall(call("kotlin", "jvm"), "version", string(version))
      } else {
        call("kotlin", "jvm")
      }
    }
  }

  override fun withKotlinTest(): Self = apply {
    withMavenCentral()
    addTestImplementationDependency(call("kotlin", "test"))
    configureTestTask {
      call("useJUnitPlatform")
    }
  }

  override fun ScriptTreeBuilder.mavenRepository(url: String): ScriptTreeBuilder = applyKt {
    call("maven", "url" to url)
  }

  override fun generate(): String {
    return KotlinScriptBuilder().generate(generateTree())
  }

  internal class Impl(gradleVersion: GradleVersion) : KotlinDslGradleBuildScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}