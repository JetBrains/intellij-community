// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.findUsages.AbstractKotlinGroupUsagesBySimilarityFeaturesTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll


abstract class AbstractKotlinGroupUsagesBySimilarityFeaturesFirTest : AbstractKotlinGroupUsagesBySimilarityFeaturesTest() {

    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

}