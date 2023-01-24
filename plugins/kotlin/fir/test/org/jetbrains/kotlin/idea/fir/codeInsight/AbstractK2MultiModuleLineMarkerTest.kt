// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.KotlinMultiplatformAnalysisModeComponent
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractLineMarkerCodeMetaInfoTest
import org.jetbrains.kotlin.idea.codeMetaInfo.renderConfigurations.LineMarkerConfiguration
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractK2MultiModuleLineMarkerTest: AbstractLineMarkerCodeMetaInfoTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiplatform")

    override fun getConfigurations() = listOf(
        LineMarkerConfiguration()
    )

    override fun setUp() {
        super.setUp()
        KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.SEPARATE)
            },
            ThrowableRunnable { super.tearDown() }
        )
    }
}