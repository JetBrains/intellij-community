// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.checkers.AbstractKotlinHighlightVisitorTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtCodeFragment
import kotlin.test.assertNull

abstract class AbstractCodeFragmentAutoImportTest : AbstractKotlinHighlightVisitorTest() {
    override fun doTest(filePath: String) {
        myFixture.configureByCodeFragment(filePath)
        myFixture.doHighlighting()

        val importFix = myFixture.availableIntentions.singleOrNull { it.familyName == "Import" }
            ?: error("No import fix available")
        importFix.invoke(project, editor, file)

        myFixture.checkResultByFile("${fileName()}.after")

        val fragment = myFixture.file as KtCodeFragment
        fragment.checkImports(dataFile())

        val fixAfter = myFixture.availableIntentions.firstOrNull { it.familyName == "Import" }
        assertNull(fixAfter, "No import fix should be available after")
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
