// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.KotlinMultiplatformAnalysisModeComponent
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractK2MultiplatformTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiplatform")

    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE)
    }

    protected open fun doTest(testPath: String) {
        setupMppProjectFromDirStructure(File(testPath))
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