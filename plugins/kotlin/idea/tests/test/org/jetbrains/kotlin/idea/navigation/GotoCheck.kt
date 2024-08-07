// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.PsiElementNavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import org.junit.Assert
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        val renderedSymbols = foundSymbols.map { getPsiTarget(it as NavigationItem)!!.renderAsGotoImplementation() }.toSet()


        if (inexactMatching) {
            UsefulTestCase.assertContainsElements(renderedSymbols, expectedReferences)
        } else {
            UsefulTestCase.assertOrderedEquals(renderedSymbols.sorted(), expectedReferences)
        }
        if (!checkNavigation) return

        assertTrue(foundSymbols.isNotEmpty())
        foundSymbols.forEach {
            assertNavigationElementMatches(getPsiTarget(it as NavigationItem), documentText)
        }
    }


    @JvmStatic
    fun checkGotoResult(
        model: FilteringGotoByModel<LanguageRef>,
        editor: Editor,
        expectedFile: Path,
    ) {
        val documentText = editor.document.text
        val searchTextList = InTextDirectivesUtils.findListWithPrefixes(documentText, SEARCH_TEXT_DIRECTIVE)

        val dumbMode = InTextDirectivesUtils.isDirectiveDefined(documentText, DUMB_MODE_DIRECTIVE)

        val searchText = searchTextList.first()
        val includeNonProjectSymbols = InTextDirectivesUtils.isDirectiveDefined(documentText, "// CHECK_BOX")

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

        val renderedSymbols = foundSymbols.mapIndexed { i, element ->
            val navigationItem = element as NavigationItem
            val psiTarget = getPsiTarget(navigationItem)
            val presentation = navigationItem.presentation
            """
                |NavigationItem:
                |    name: ${navigationItem.name}
                |ItemPresentation:
                |    presentableText: ${presentation?.presentableText}
                |    locationString: ${presentation?.locationString}
                |    icon: ${presentation?.getIcon(false)}
                |TargetElement: ${psiTarget?.text?.lines()?.first()?.trim()}
                |QualifiedName: ${model.getFullName(element)}
            """.trimMargin()
        }
            .sorted()
            .joinToString(separator = "\n\n")


        UsefulTestCase.assertSameLinesWithFile(expectedFile.pathString, renderedSymbols)
    }

    private fun getPsiTarget(
        navigationItem: NavigationItem,
    ): PsiElement? = when (navigationItem) {
        is PsiElementNavigationItem -> navigationItem.targetElement
        is PsiElement -> navigationItem
        else -> error("Unexpected NavigationItem ${navigationItem::class}")
    }

    @JvmStatic
    fun assertNavigationElementMatches(resolved: PsiElement?, textWithDirectives: String) {
        assertNotNull(resolved)
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