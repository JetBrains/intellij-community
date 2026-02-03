// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.slicer

import com.intellij.slicer.SliceLanguageSupportProvider
import org.jetbrains.kotlin.idea.codeInsight.slicer.HackedSliceNullnessAnalyzerBase

abstract class AbstractK1SlicerNullnessGroupingTest: AbstractSlicerNullnessGroupingTest() {
    override fun createNullnessAnalyzer(sliceProvider: SliceLanguageSupportProvider): HackedSliceNullnessAnalyzerBase {
        return (sliceProvider as KotlinSliceProvider).nullnessAnalyzer
    }
}
