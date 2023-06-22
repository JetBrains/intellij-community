// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import kotlin.apply as applyKt

@ApiStatus.Internal
class KotlinDslGradleSettingScriptBuilder : AbstractGradleSettingScriptBuilder<KotlinDslGradleSettingScriptBuilder>() {

  override fun apply(action: KotlinDslGradleSettingScriptBuilder.() -> Unit) = applyKt(action)

  override fun generate(): String {
    return KotlinScriptBuilder().generate(generateTree())
  }
}