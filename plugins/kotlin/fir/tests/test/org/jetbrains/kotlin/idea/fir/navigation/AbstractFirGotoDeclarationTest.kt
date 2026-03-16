// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.navigation

import org.jetbrains.kotlin.idea.navigation.AbstractGotoDeclarationTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFirGotoDeclarationTest : AbstractGotoDeclarationTest() {

  override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun tearDown() {
    runAll(
        { project.invalidateCaches() },
        { super.tearDown() },
    )
  }
}