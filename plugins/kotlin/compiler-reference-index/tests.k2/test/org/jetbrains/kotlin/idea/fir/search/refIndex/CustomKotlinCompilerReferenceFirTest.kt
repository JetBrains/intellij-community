// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import com.intellij.testFramework.SkipSlowTestLocally
import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.CallableOverridingsTooltip
import org.jetbrains.kotlin.idea.codeInsight.lineMarkers.ClassInheritorsTooltip
import org.jetbrains.kotlin.idea.search.refIndex.CustomKotlinCompilerReferenceTest6

@SkipSlowTestLocally
class CustomKotlinCompilerReferenceFirTest : CustomKotlinCompilerReferenceTest6() {
    override val isFir: Boolean get() = true

    override fun testTooltips() {
        doTestTooltips(ClassInheritorsTooltip, CallableOverridingsTooltip)
    }
}
