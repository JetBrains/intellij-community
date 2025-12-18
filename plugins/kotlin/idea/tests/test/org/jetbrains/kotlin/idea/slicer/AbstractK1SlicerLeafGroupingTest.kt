// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceLanguageSupportProvider
import com.intellij.slicer.SliceLeafAnalyzer

abstract class AbstractK1SlicerLeafGroupingTest: AbstractSlicerLeafGroupingTest() {
    override fun createAnalyzer(sliceProvider: SliceLanguageSupportProvider): SliceLeafAnalyzer  {
        return (sliceProvider as KotlinSliceProvider).leafAnalyzer
    }
}

