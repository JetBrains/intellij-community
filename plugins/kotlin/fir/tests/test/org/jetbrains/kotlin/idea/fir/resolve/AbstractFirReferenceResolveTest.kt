// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFirReferenceResolveTest : AbstractReferenceResolveTest() {

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor = PROJECT_DESCRIPTOR

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    companion object {
        private val PROJECT_DESCRIPTOR = object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            override fun getSdk(): Sdk = PluginTestCaseBase.fullJdk().clone().apply {
                sdkModificator.apply {
                    name = "Full JDK without sources"
                    removeRoots(OrderRootType.SOURCES)
                    applyChangesWithoutWriteAction()
                }
            }
        }
    }
}