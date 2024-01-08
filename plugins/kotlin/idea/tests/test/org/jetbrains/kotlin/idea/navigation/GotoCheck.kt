// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.junit.Assert
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object GotoCheck {

    private const val SEARCH_TEXT_DIRECTIVE: String = "// SEARCH_TEXT:"
    private const val DUMB_MODE_DIRECTIVE: String = "// DUMB_MODE"

    @JvmStatic
    @JvmOverloads
    fun checkGotoDirectives(
        model: FilteringGotoByModel<LanguageRef>,
        editor: Editor,
        nonProjectSymbols: Boolean = false,
        checkNavigation: Boolean = false
    ) {
        val documentText = editor.document.text
        val searchTextList = InTextDirectivesUtils.findListWithPrefixes(documentText, SEARCH_TEXT_DIRECTIVE)
        Assert.assertFalse(
            "There's no search text in test data file given. Use '$SEARCH_TEXT_DIRECTIVE' directive",
            searchTextList.isEmpty()
        )

        val expectedReferences =
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(documentText, "// REF:").map { input -> input.trim { it <= ' ' } }
        val includeNonProjectSymbols = nonProjectSymbols || InTextDirectivesUtils.isDirectiveDefined(documentText, "// CHECK_BOX")

        val dumbMode = InTextDirectivesUtils.isDirectiveDefined(documentText, DUMB_MODE_DIRECTIVE)

        val searchText = searchTextList.first()

        val symbolsTask: () -> List<Any?> = {
            val names = model.getNames(includeNonProjectSymbols)
            names.filter { it?.startsWith(searchText) == true }.flatMap {
                model.getElementsByName(it, includeNonProjectSymbols, "$it*").toList()
            }
        }
        val foundSymbols = if (dumbMode) {
            val project = editor.project!!
            // to trigger indexing
            symbolsTask()

            val result = AtomicReference<List<Any?>>(emptyList<Any?>())
            DumbModeTestUtils.runInDumbModeSynchronously(project) { result.set(symbolsTask()) }
            result.get()
        } else {
            symbolsTask()
        }

        val inexactMatching = InTextDirectivesUtils.isDirectiveDefined(documentText, "// ALLOW_MORE_RESULTS")
        val renderedSymbols = foundSymbols.map { (it as PsiElement).renderAsGotoImplementation() }.toSet()

        if (checkNavigation && (expectedReferences.size != 1 || inexactMatching)) {
            error("Cannot check navigation targets when multiple references are expected")
        }

        if (inexactMatching) {
            UsefulTestCase.assertContainsElements(renderedSymbols, expectedReferences)
        } else {
            UsefulTestCase.assertOrderedEquals(renderedSymbols.sorted(), expectedReferences)
        }
        if (!checkNavigation) return

        assertTrue(foundSymbols.isNotEmpty())
        foundSymbols.forEach {
            assertNavigationElementMatches(it as PsiElement, documentText)
        }
    }

    @JvmStatic
    fun assertNavigationElementMatches(resolved: PsiElement, textWithDirectives: String) {
        val expectedBinaryFile = InTextDirectivesUtils.findStringWithPrefixes(textWithDirectives, "// BINARY:")
        val expectedSourceFile = InTextDirectivesUtils.findStringWithPrefixes(textWithDirectives, "// SRC:")
        assertEquals(expectedBinaryFile, getFileWithDir(resolved))
        val srcElement = resolved.navigationElement
        Assert.assertNotEquals(srcElement, resolved)
        assertEquals(expectedSourceFile, getFileWithDir(srcElement))
    }

    // TODO: move somewhere
    fun getFileWithDir(resolved: PsiElement): String {
        val targetFile = resolved.containingFile
        val targetDir = targetFile.parent
        return targetDir!!.name + "/" + targetFile.name
    }
}