// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.highlighter.AbstractCustomHighlightUsageHandlerTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class AbstractK2HighlightExitPointsTest: AbstractCustomHighlightUsageHandlerTest() {

    override fun setUp() {
        super.setUp()
        Registry.get("kotlin.highlight.stdlib.dsl.exit.points").setValue(true, testRootDisposable)
    }

    override fun doTest(unused: String) {
        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
        try {
            super.doTest(unused)
        } finally {
            ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
        }
    }
    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromFileDirective()

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}