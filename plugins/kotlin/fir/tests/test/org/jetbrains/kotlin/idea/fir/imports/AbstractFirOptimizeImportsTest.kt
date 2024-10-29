// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.imports

import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.imports.KotlinFirImportOptimizer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirJvmOptimizeImportsTest : AbstractImportsTest() {

    override val runTestInWriteCommand: Boolean = false

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
          dataFile().toPath(),
          IgnoreTests.DIRECTIVES.IGNORE_K2,
          ".after",
          test = { super.doTest(unused) }
        )
    }

    override fun doTest(file: KtFile): String? {
        val optimizer = executeOnPooledThreadInReadAction {
            KotlinFirImportOptimizer().processFile(file)
        }

        project.executeWriteCommand("") {
            optimizer.run()
        }
        userNotificationInfo = optimizer.userNotificationInfo

        return null
    }

    override val nameCountToUseStarImportDefault: Int
        get() = Integer.MAX_VALUE
}