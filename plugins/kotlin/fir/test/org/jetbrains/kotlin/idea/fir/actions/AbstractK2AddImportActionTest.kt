// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.actions

import org.jetbrains.kotlin.idea.actions.AbstractAddImportActionTestBase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractK2AddImportActionTest : AbstractAddImportActionTestBase() {
    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(dataFilePath(), IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(unused)
        }
    }
}