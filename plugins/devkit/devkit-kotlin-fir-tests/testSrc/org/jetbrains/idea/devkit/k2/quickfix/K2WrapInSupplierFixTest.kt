// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.quickfix

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.kotlin.inspections.quickfix.KtWrapInSupplierFixTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

@TestApplication
class K2WrapInSupplierFixTest : KtWrapInSupplierFixTest(projectFixture) {
  companion object {
    private val projectFixture = lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithReflect())
  }
}