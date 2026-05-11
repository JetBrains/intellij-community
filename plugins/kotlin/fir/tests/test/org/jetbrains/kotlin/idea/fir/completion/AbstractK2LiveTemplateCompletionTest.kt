// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.NewLightKotlinCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.completion.test.handlers.CompletionHandlerTestBase

abstract class AbstractK2LiveTemplateCompletionTest : NewLightKotlinCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }

    protected fun doTest(testPath: String) {
        myFixture.configureByDefaultFile()

        val fileText = myFixture.file.text

        val lookupString = InTextDirectivesUtils.findStringWithPrefixes(fileText, ELEMENT_PREFIX)
            ?: error("Missing // $ELEMENT_PREFIX directive")
        val itemText = InTextDirectivesUtils.findStringWithPrefixes(fileText, ITEM_TEXT_PREFIX)
        val tailText = InTextDirectivesUtils.findStringWithPrefixes(fileText, TAIL_TEXT_PREFIX)
        val tabCount = InTextDirectivesUtils.getPrefixedInt(fileText, TABS_PREFIX) ?: 0
        val escapeCount = InTextDirectivesUtils.getPrefixedInt(fileText, ESCAPES_DIRECTIVE) ?: 0

        myFixture.complete(CompletionType.BASIC)

        val item = CompletionHandlerTestBase.getExistentLookupElement(project, lookupString, itemText, tailText)
        val lookup = myFixture.lookup as? LookupImpl
        if (item != null && lookup != null) {
            lookup.currentItem = item
            lookup.lookupFocusDegree = LookupFocusDegree.FOCUSED
            lookup.finishLookup('\n')
        }

        repeat(tabCount) {
            myFixture.type("\t")
        }
        repeat(escapeCount) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE)
        }

        myFixture.checkContentByExpectedPath(".after", addSuffixAfterExtension = true)
    }

    private companion object {
        const val ELEMENT_PREFIX = "ELEMENT:"
        const val ITEM_TEXT_PREFIX = "ITEM_TEXT:"
        const val TAIL_TEXT_PREFIX = "TAIL_TEXT:"
        const val TABS_PREFIX = "TABS:"
        const val ESCAPES_DIRECTIVE = "ESCAPES:"
    }
}
