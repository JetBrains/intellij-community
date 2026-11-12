// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyForeachCompletionTest {
  private val groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_5_0, "groovy/completion/foreach")
  private val completion by groovyCompletionFixture(groovy)

  @Test
  fun testValueVariableType() = runInEdtAndWait { completion.doHasVariantsTest("int", "final") }

  @Test
  fun testValueVariableTypeWithIndexVariable() = runInEdtAndWait { completion.doVariantableTest("int", "final") }

  @Test
  fun testIndexVariableType() = runInEdtAndWait { completion.doVariantableTest("int", "final") }

  @Test
  fun testIndexVariableAccess() = runInEdtAndWait { completion.doVariantableTest("idx", "identity", "void") }

  @Test
  fun testValueVariableAccess() = runInEdtAndWait { completion.doVariantableTest("value", "var", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues") }

  @Test
  fun testValueVariableAccessWithIndexVariable() = runInEdtAndWait { completion.doVariantableTest("value", "var", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues") }
}