// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerLeafGroupingTest : AbstractSlicerTest() {
    override fun doTest(path: String, sliceProvider: KotlinSliceProvider, rootNode: SliceRootNode) {
        val treeStructure = TestSliceTreeStructure(rootNode)
        val analyzer = sliceProvider.leafAnalyzer
        val possibleElementsByNode = analyzer.createMap()
        val leafExpressions = analyzer.calcLeafExpressions(rootNode, treeStructure, possibleElementsByNode)
        val newRootNode = analyzer.createTreeGroupedByValues(leafExpressions, rootNode, possibleElementsByNode)
        val renderedForest = buildString {
            newRootNode.children.map { groupRootNode -> buildTreeRepresentation(groupRootNode) }.sorted().forEach {
                append(it)
                append("\n")
            }
        }
        KotlinTestUtils.assertEqualsToFile(File(path.replace(".kt", ".leafGroups.txt")), renderedForest)
    }
}
