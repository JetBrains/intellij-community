// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyForeachCompletionTest : GroovyCompletionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getBasePath(): String {
    return TestUtils.getTestDataPath() + "groovy/completion/foreach"
  }

  fun testValueVariableType() = doVariantableTest("int", "final")

  fun testValueVariableTypeWithIndexVariable() = doVariantableTest("int", "final")

  fun testIndexVariableType() = doVariantableTest("int", "final")

  fun testIndexVariableAccess() = doVariantableTest("idx", "identity", "void")

  fun testValueVariableAccess() = doVariantableTest("value", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues")

  fun testValueVariableAccessWithIndexVariable() = doVariantableTest("value", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues")
}