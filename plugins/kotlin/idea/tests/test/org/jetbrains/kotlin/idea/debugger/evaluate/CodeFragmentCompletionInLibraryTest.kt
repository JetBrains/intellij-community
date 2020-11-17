/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.completion.test.AbstractJvmBasicCompletionTest
import org.jetbrains.kotlin.idea.completion.test.testCompletion
import org.jetbrains.kotlin.idea.debugger.getContextElement
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.test.KotlinRoot
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File


@RunWith(JUnit38ClassRunner::class)
class CodeFragmentCompletionInLibraryTest : AbstractJvmBasicCompletionTest() {
    companion object {
        private val LIBRARY_SRC = File(KotlinRoot.DIR, "completion/testData/codeFragmentInLibrarySource/customLibrary/")
    }

    private val mockLibraryFacility = MockLibraryFacility(source = LIBRARY_SRC)
    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() = runAll(
        ThrowableRunnable { mockLibraryFacility.tearDown(module) },
        ThrowableRunnable { super.tearDown() }
    )

    fun testCompletionInCustomLibrary() {
        testCompletionInLibraryCodeFragment("<caret>", "EXIST: parameter")
    }

    fun testSecondCompletionInCustomLibrary() {
        testCompletionInLibraryCodeFragment("Ch<caret>", "EXIST: CharRange", "EXIST: Char", "INVOCATION_COUNT: 2")
    }

    fun testExtensionCompletionInCustomLibrary() {
        testCompletionInLibraryCodeFragment("3.extOn<caret>", "EXIST: extOnInt")
    }

    fun testJavaTypesCompletion() {
        testCompletionInLibraryCodeFragment("Hash<caret>", "EXIST: HashMap", "EXIST: HashSet")
    }

    private fun testCompletionInLibraryCodeFragment(fragmentText: String, vararg completionDirectives: String) {
        setupFixtureByCodeFragment(fragmentText)
        val directives = completionDirectives.joinToString(separator = "\n") { "//$it" }
        testCompletion(directives,
                       JvmPlatforms.unspecifiedJvmPlatform, { completionType, count -> myFixture.complete(completionType, count) })
    }

    private fun setupFixtureByCodeFragment(fragmentText: String) {
        val sourceFile = findLibrarySourceDir().findChild("customLibrary.kt")!!
        val jetFile = PsiManager.getInstance(project).findFile(sourceFile) as KtFile
        val fooFunctionFromLibrary = jetFile.declarations.first() as KtFunction
        val codeFragment = KtPsiFactory(fooFunctionFromLibrary).createExpressionCodeFragment(
            fragmentText,
            getContextElement(fooFunctionFromLibrary.bodyExpression)
        )
        codeFragment.forceResolveScope(GlobalSearchScope.allScope(project))
        myFixture.configureFromExistingVirtualFile(codeFragment.virtualFile)
    }

    private fun findLibrarySourceDir(): VirtualFile {
        return LocalFileSystem.getInstance().findFileByIoFile(LIBRARY_SRC)!!
    }
}
