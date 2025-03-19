// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class GroovyDslGradleSettingScriptBuilder<Self : GroovyDslGradleSettingScriptBuilder<Self>>(
  gradleVersion: GradleVersion,
) : AbstractGradleSettingScriptBuilder<Self>(gradleVersion) {

  override fun setProjectDir(name: String, relativePath: String): Self =
    addCode("""project('$name').projectDir = file('$relativePath')""")

  override fun generate(): String {
    return GroovyScriptBuilder().generate(generateTree())
  }

  internal class Impl(gradleVersion: GradleVersion) : GroovyDslGradleSettingScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}