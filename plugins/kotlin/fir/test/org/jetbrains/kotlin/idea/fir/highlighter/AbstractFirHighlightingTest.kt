// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.addExternalTestFiles
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractFirHighlightingTest : AbstractHighlightingTest() {
    override val captureExceptions: Boolean = false

    override fun getDefaultProjectDescriptor() = ProjectDescriptorWithStdlibSources.INSTANCE

    override fun isFirPlugin() = true

    override fun doTest(unused: String?) {
        addExternalTestFiles(testPath())
        super.doTest(unused)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun checkHighlighting(fileText: String) {
        val checkInfos = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_INFOS_PREFIX);

        IgnoreTests.runTestIfNotDisabledByFileDirective(testDataFile().toPath(), IgnoreTests.DIRECTIVES.IGNORE_FIR) {
            // warnings are not supported yet
            myFixture.checkHighlighting(/* checkWarnings= */ false, checkInfos, /* checkWeakWarnings= */ false)
        }
    }
}