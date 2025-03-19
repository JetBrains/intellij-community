// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.findUsages.AbstractFindUsagesTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.kmp.KMPProjectDescriptorTestUtilities
import org.jetbrains.kotlin.idea.test.kmp.KMPTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractFindUsagesFirTest : AbstractFindUsagesTest(), KMPTest {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KMPProjectDescriptorTestUtilities.createKMPProjectDescriptor(testPlatform)
            ?: super.getProjectDescriptor()
    }

    override val ignoreLog: Boolean
        get() = true

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}