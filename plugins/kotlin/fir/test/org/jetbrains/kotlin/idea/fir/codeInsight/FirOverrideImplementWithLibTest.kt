// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.codeInsight

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codeInsight.OverrideImplementWithLibTest
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.TestRoot
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestMetadata("testData/codeInsight/overrideImplement/withLib")
@RunWith(JUnit38ClassRunner::class)
internal class FirOverrideImplementWithLibTest : OverrideImplementWithLibTest<KtClassMember>(), FirOverrideImplementTestMixIn {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            // If we pass something as a context, then the DependencyListForCliModule will be built with dependencies from the previous test
            // and will not be reinitialized later on. Because of that the first test might pass, but the other ones probably won't
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}

