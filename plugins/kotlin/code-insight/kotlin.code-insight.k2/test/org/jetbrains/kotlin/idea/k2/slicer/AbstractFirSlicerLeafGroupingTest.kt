// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.slicer;

import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceLeafAnalyzer
import com.intellij.slicer.SliceRootNode
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.k2.codeinsight.slicer.KotlinSliceProvider
import org.jetbrains.kotlin.idea.slicer.AbstractSlicerLeafGroupingTest
import java.io.File

abstract class AbstractFirSlicerLeafGroupingTest: AbstractSlicerLeafGroupingTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
    override fun createAnalyzer(sliceProvider: SliceLanguageSupportProvider): SliceLeafAnalyzer {
        return (sliceProvider as KotlinSliceProvider).leafAnalyzer
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun doTest(
        path: String,
        sliceProvider: SliceLanguageSupportProvider,
        rootNode: SliceRootNode
    ) {
        allowAnalysisOnEdt {
            super.doTest(path, sliceProvider, rootNode)
        }
    }

    override fun getResultFile(path: String): File {
        val file = File(path.replace(".kt", ".k2.leafGroups.txt"))
        if (file.exists()) {
            return file
        }
        return super.getResultFile(path)
    }

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }
}
