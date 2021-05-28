// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.findUsages.AbstractKotlinFindUsagesWithStdlibTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinFindUsagesWithStdlibFirTest : AbstractKotlinFindUsagesWithStdlibTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        project.invalidateCaches(file as? KtFile)
        super.tearDown()
    }

    override fun <T : PsiElement> doTest(path: String) = doTestWithFIRFlagsByPath(path) {
        super.doTest<T>(path)
    }
}
