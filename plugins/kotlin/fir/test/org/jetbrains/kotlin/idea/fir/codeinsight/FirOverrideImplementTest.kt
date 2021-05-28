// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.codeinsight

import org.jetbrains.kotlin.idea.codeInsight.OverrideImplementTest
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.invalidateCaches
import org.jetbrains.kotlin.psi.KtFile
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
internal class FirOverrideImplementTest : OverrideImplementTest<KtClassMember>(), FirOverrideImplementTestMixIn {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        project.invalidateCaches(file as? KtFile)
        super.tearDown()
    }
}

