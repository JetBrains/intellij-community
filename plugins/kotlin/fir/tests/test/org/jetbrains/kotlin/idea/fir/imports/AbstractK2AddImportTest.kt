// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.imports

import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2AddImportTest : AbstractImportsTest() {

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFile().toPath(),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            ".after",
            test = { super.doTest(unused) }
        )
    }

    override fun doTest(file: KtFile): String? {
        var descriptorName = InTextDirectivesUtils.stringWithDirective(file.text, "IMPORT")
        if (descriptorName.startsWith("class:")) {
            descriptorName = descriptorName.substring("class:".length).trim()
        }

        file.addImport(FqName(descriptorName))
        return null
    }
}
