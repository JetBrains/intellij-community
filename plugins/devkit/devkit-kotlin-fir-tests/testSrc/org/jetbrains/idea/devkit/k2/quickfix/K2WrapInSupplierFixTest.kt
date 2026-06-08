// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.quickfix

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.kotlin.inspections.quickfix.KtWrapInSupplierFixTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class K2WrapInSupplierFixTest : KtWrapInSupplierFixTest() {


  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithReflect()
  }
}
