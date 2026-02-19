// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.sessions

import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLResolutionFacadeService
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.ensureFilesResolved
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.addTypeArgument
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.io.File

/**
 * Almost all dangling file session invalidation tests can be found in `AbstractSessionInvalidationTest` on the Analysis API side.
 * [DanglingFileModuleInvalidationTest] tests fake dangling file session invalidation because the Analysis API test infrastructure doesn't
 * support fake files yet.
 */
class DanglingFileModuleInvalidationTest : AbstractMultiModuleTest() {

    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testCodeFragmentChangeInsideFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = true)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val fakeBarCall = fakeFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", fakeBarCall)
        ensureFilesResolved(codeFragment, fakeFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            fakeBarCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
        }

        assert(!sessionBefore.isValid)
        assert(sessionAfter.isValid)
    }

    fun testCodeFragmentChangeInsideUnrelatedFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = true)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val barCall = srcFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "fragment.kt", "bar<Int>()", "", barCall)
        ensureFilesResolved(codeFragment, fakeFile)

        val (sessionBefore, sessionAfter) = performModification(codeFragment) {
            val fakeBarCall = fakeFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!
            fakeBarCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
        }

        assertEquals(sessionBefore, sessionAfter)
        assert(sessionAfter.isValid)
    }

    fun testNonPhysicalFakeFile() {
        val srcModule = createModuleInTmpDir("src") {
            val srcFile = FileWithText(
                "test.kt",
                """
                    fun foo() {
                        <caret>bar()
                    }
                    
                    fun <T> bar() {}
                """.trimIndent()
            )

            listOf(srcFile)
        }

        val srcFile = srcModule.findSourceKtFile("test.kt")

        val ktPsiFactory = KtPsiFactory.contextual(srcFile, markGenerated = true, eventSystemEnabled = false)
        val fakeFile = ktPsiFactory.createFile("fake.txt", srcFile.text)

        val (sessionBefore, sessionAfter) = performModification(fakeFile) {
            // Do nothing
        }

        assert(sessionBefore.isValid)
        assertTrue(sessionBefore === sessionAfter)

        val (sessionBeforeWithPsiModification, sessionAfterWithPsiModification) = performModification(fakeFile) {
            PsiManager.getInstance(project).dropPsiCaches()
        }

        assert(!sessionBeforeWithPsiModification.isValid)
        assert(sessionAfterWithPsiModification.isValid)
    }

    private fun performModification(element: KtElement, modificationBlock: () -> Unit): Pair<LLFirSession, LLFirSession> {
        val sessionBefore = getSession(element)
        project.executeWriteCommand("Context modification") {
            modificationBlock()
        }
        val sessionAfter = getSession(element)
        return Pair(sessionBefore, sessionAfter)
    }

    @OptIn(LLFirInternals::class)
    private fun getSession(element: KtElement): LLFirSession {
        val module = KotlinProjectStructureProvider.getModule(project, element, useSiteModule = null)
        val resolutionFacade = LLResolutionFacadeService.getInstance(project).getResolutionFacade(module)
        return resolutionFacade.getSessionFor(module)
    }
}
