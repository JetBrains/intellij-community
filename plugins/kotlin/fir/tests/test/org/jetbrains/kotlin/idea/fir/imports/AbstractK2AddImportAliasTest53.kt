// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.imports

import org.jetbrains.kotlin.AbstractImportsTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.k2.refactoring.introduceImportAlias.KotlinIntroduceImportAliasHandler
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.utils.sure

abstract class AbstractK2AddImportAliasTest53 : AbstractImportsTest() {

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
        val element = findNameReferenceExpression()
        KotlinIntroduceImportAliasHandler.doRefactoring(project, editor, element)
        return null
    }

    private fun findNameReferenceExpression(): KtNameReferenceExpression {
        val offset = myFixture.caretOffset
        var element = file.findElementAt(offset)
        while (element != null) {
            if (element is KtNameReferenceExpression || element is KtFile) break
            if (offset !in element.textRange) break
            element = element.parent
        }
        return (element as? KtNameReferenceExpression).sure {
            "caret has to be placed at KtNameReferenceExpression, current position at $offset"
        }
    }

    override fun registerClassImportFilterExtensions(classImportFilterVetoRegexRules: MutableList<String>) {
        // Not supported in K2 Mode (KTIJ-37810)
    }
}
