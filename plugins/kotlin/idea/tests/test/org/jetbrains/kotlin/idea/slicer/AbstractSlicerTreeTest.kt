// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerTreeTest : AbstractSlicerTest() {
    override fun doTest(path: String, sliceProvider: SliceLanguageSupportProvider, rootNode: SliceRootNode) {
        KotlinTestUtils.assertEqualsToFile(getResultsFile(path),
                                           ActionUtil.underModalProgress(project, "") {  buildTreeRepresentation(rootNode) })
    }

    protected open fun getResultsFile(path: String) = File(path.replace(".kt", ".results.txt"))
}