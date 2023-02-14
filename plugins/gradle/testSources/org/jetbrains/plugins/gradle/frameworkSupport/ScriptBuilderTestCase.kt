// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.frameworkSupport.script.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.script
import org.junit.jupiter.api.Assertions.assertEquals

abstract class ScriptBuilderTestCase {

  fun assertScript(
    groovyScript: String,
    kotlinScript: String,
    configure: ScriptTreeBuilder.() -> Unit
  ) {
    assertEquals(groovyScript, script(useKotlinDsl = false, configure))
    assertEquals(kotlinScript, script(useKotlinDsl = true, configure))
  }
}