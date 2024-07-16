// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.junit.jupiter.api.Assertions

abstract class GradleSettingsScriptBuilderTestCase {

  fun assertBuildSettings(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleSettingScriptBuilder<*>.() -> Unit
  ) {
    val actualGroovyScript = GradleSettingScriptBuilder.create(useKotlinDsl = false).apply(configure).generate()
    val actualKotlinScript = GradleSettingScriptBuilder.create(useKotlinDsl = true).apply(configure).generate()
    Assertions.assertEquals(groovyScript, actualGroovyScript) {
      "Groovy scripts should be equal"
    }
    Assertions.assertEquals(kotlinScript, actualKotlinScript) {
      "Kotlin scripts should be equal"
    }
  }
}