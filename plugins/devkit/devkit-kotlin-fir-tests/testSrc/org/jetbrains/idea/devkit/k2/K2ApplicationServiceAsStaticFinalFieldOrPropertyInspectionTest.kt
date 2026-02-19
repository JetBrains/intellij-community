// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.kotlin.inspections.KtApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class K2ApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest : KtApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest() {

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithReflect()
  }
}
