// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.findUsages

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.findUsages.FindUsagesMultiModuleTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.runAll

class FindUsagesMultiModuleFirTest : FindUsagesMultiModuleTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun doFindUsagesTest() {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            getTestdataFile().toPath().resolve("directives.txt"),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doFindUsagesTest()
        }
    }
}