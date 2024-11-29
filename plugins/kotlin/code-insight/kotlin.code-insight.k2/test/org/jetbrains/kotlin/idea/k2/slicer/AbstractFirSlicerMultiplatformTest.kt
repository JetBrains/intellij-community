// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.slicer

import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.slicer.KotlinSliceProvider
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerMultiplatformTest
import java.io.File

abstract class AbstractFirSlicerMultiplatformTest: AbstractSlicerMultiplatformTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
    override fun createSliceProvider(): SliceLanguageSupportProvider = KotlinSliceProvider()

    override fun getResultsFile(testRoot: File): File {
        val file = testRoot.resolve("k2.results.txt")
        if (file.exists()) {
            return file
        }
        return super.getResultsFile(testRoot)
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }
}