// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInspection.ex.EntryPointsManagerBase

/**
 * Test which runs both [com.intellij.codeInsight.daemon.impl.LocalInspectionsPass] and [com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass]
 */
abstract class AbstractK2LocalInspectionAndGeneralHighlightingTest : AbstractK2LocalInspectionTest() {
    override fun setUp() {
        super.setUp()
        // to be able to declare some method as an entry point in tests, without any unnecessary extra dependencies
        EntryPointsManagerBase.getInstance(project).ADDITIONAL_ANNOTATIONS.add("TemporaryTestingFakeEntryPointAnnotation")
    }

    override fun tearDown() {
        EntryPointsManagerBase.getInstance(project).ADDITIONAL_ANNOTATIONS.remove("TemporaryTestingFakeEntryPointAnnotation")
        super.tearDown()
    }

    override fun passesToIgnore(): IntArray {
        return intArrayOf(
            Pass.LINE_MARKERS,
            Pass.SLOW_LINE_MARKERS,
            Pass.EXTERNAL_TOOLS,
            Pass.POPUP_HINTS,
            Pass.UPDATE_FOLDING,
            Pass.WOLF
        )
    }
}