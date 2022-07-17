// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceRootNode
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractSlicerTreeTest : AbstractSlicerTest() {
    override fun doTest(path: String, sliceProvider: KotlinSliceProvider, rootNode: SliceRootNode) {
        KotlinTestUtils.assertEqualsToFile(File(path.replace(".kt", ".results.txt")), buildTreeRepresentation(rootNode))
    }
}