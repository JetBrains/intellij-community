// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class KotlinDslGradleSettingScriptBuilder<Self : KotlinDslGradleSettingScriptBuilder<Self>>
  : AbstractGradleSettingScriptBuilder<Self>() {

  override fun setProjectDir(name: String, relativePath: String): Self =
    addCode("""project("$name").projectDir = file("$relativePath")""")

  override fun generate(): String {
    return KotlinScriptBuilder().generate(generateTree())
  }

  internal class Impl : KotlinDslGradleSettingScriptBuilder<Impl>() {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}