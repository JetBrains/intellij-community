// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceLeafAnalyzer
import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerLeafGroupingTest : AbstractSlicerTest() {
    protected open fun createAnalyzer(sliceProvider: SliceLanguageSupportProvider): SliceLeafAnalyzer {
        return (sliceProvider as KotlinSliceProvider).leafAnalyzer
    }

    override fun doTest(path: String, sliceProvider: SliceLanguageSupportProvider, rootNode: SliceRootNode) {
        val renderedForest = ActionUtil.underModalProgress(project, "") {
            val treeStructure = TestSliceTreeStructure(rootNode)
            val analyzer = createAnalyzer(sliceProvider)
            val possibleElementsByNode = analyzer.createMap()
            val leafExpressions = analyzer.calcLeafExpressions(rootNode, treeStructure, possibleElementsByNode)
            val newRootNode = analyzer.createTreeGroupedByValues(leafExpressions, rootNode, possibleElementsByNode)
            buildString {
                newRootNode.children.map { groupRootNode -> buildTreeRepresentation(groupRootNode) }.sorted().forEach {
                    this.append(it)
                    this.append("\n")
                }
            }
        }
        KotlinTestUtils.assertEqualsToFile(getResultFile(path), renderedForest)
    }

    protected open fun getResultFile(path: String): File = File(path.replace(".kt", ".leafGroups.txt"))
}
