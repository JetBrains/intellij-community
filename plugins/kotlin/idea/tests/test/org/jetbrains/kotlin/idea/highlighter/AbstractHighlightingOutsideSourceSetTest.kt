// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest

abstract class AbstractHighlightingOutsideSourceSetTest : AbstractKotlinHighlightVisitorTest() {
    fun testFileOutsideSourceSet() {
        val file = myFixture.configureByText("outsideSourceSet.kt", "")
        OutsidersPsiFileSupport.markFile(file.virtualFile)
        assertFalse("File outside source set shouldn't be highlighted", ProblemHighlightFilter.shouldHighlightFile(file))
    }
}