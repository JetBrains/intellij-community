// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.findUsages.AbstractKotlinFindUsagesWithStdlibTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractKotlinFindUsagesWithStdlibFirTest : AbstractKotlinFindUsagesWithStdlibTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

}
