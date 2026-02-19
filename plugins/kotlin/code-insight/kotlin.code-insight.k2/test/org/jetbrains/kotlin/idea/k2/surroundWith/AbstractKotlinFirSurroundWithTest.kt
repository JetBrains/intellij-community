// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.surroundWith

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.AbstractSurroundWithTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractKotlinFirSurroundWithTest: AbstractSurroundWithTest() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun tearDown() {
    runAll(
        { project.invalidateCaches() },
        { super.tearDown() },
    )
  }
}