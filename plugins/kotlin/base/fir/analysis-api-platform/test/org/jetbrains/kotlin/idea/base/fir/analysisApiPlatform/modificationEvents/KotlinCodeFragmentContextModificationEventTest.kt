// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.idea.base.test.ensureFilesResolved
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.addTypeArgument
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class KotlinCodeFragmentContextModificationEventTest : AbstractKotlinModuleModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.CODE_FRAGMENT_CONTEXT_MODIFICATION

    fun `test code fragment context modification does not occur after no changes`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") { }

            contextModuleTracker.assertNotModified()
            unrelatedModuleTracker.assertNotModified()
            codeFragmentTracker.assertNotModified()
            nestedCodeFragmentTracker.assertNotModified()
            unrelatedCodeFragmentTracker.assertNotModified()
        }
    }

    fun `test code fragment context modification in the root code fragment`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") {
                val barCall = codeFragment.findDescendantOfType<KtCallExpression> { it.text == "bar<Int>()" }!!
                barCall.typeArguments[0].delete()
            }

            contextModuleTracker.assertNotModified()
            unrelatedModuleTracker.assertNotModified()
            codeFragmentTracker.assertModified()
            nestedCodeFragmentTracker.assertNotModified()
            unrelatedCodeFragmentTracker.assertNotModified()
        }
    }

    fun `test code fragment context modification in the nested code fragment`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") {
                val barCall = nestedCodeFragment.findDescendantOfType<KtCallExpression> { it.text == "bar<String>()" }!!
                barCall.typeArguments[0].delete()
            }

            contextModuleTracker.assertNotModified()
            unrelatedModuleTracker.assertNotModified()
            codeFragmentTracker.assertNotModified()
            nestedCodeFragmentTracker.assertModified()
            unrelatedCodeFragmentTracker.assertNotModified()
        }
    }

    fun `test code fragment context modification in the unrelated code fragment`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") {
                val barCall = unrelatedCodeFragment.findDescendantOfType<KtCallExpression> { it.text == "bar<Double>()" }!!
                barCall.typeArguments[0].delete()
            }

            contextModuleTracker.assertNotModified()
            unrelatedModuleTracker.assertNotModified()
            codeFragmentTracker.assertNotModified()
            nestedCodeFragmentTracker.assertNotModified()
            unrelatedCodeFragmentTracker.assertModified()
        }
    }

    fun `test code fragment context modification in the context module`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") {
                val ktPsiFactory = KtPsiFactory(project, markGenerated = false)
                val barCall = contextModuleSourceFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!
                barCall.addTypeArgument(ktPsiFactory.createTypeArgument("Any"))
            }

            contextModuleTracker.assertModified()
            unrelatedModuleTracker.assertNotModified()
            codeFragmentTracker.assertNotModified()
            nestedCodeFragmentTracker.assertNotModified()
            unrelatedCodeFragmentTracker.assertNotModified()
        }
    }

    fun `test code fragment context modification in the unrelated module`() {
        with(createModuleStructure()) {
            project.executeWriteCommand("Code fragment modification") {
                val variableDeclaration = unrelatedModuleSourceFile.findDescendantOfType<KtVariableDeclaration> { it.name == "y" }!!
                variableDeclaration.setName("z")
            }

            contextModuleTracker.assertNotModified()
            unrelatedModuleTracker.assertModified()
            codeFragmentTracker.assertNotModified()
            nestedCodeFragmentTracker.assertNotModified()
            unrelatedCodeFragmentTracker.assertNotModified()
        }
    }

    private class SharedCodeFragmentModuleStructure(
        val contextModuleSourceFile: KtFile,
        val unrelatedModuleSourceFile: KtFile,
        val codeFragment: KtBlockCodeFragment,
        val nestedCodeFragment: KtBlockCodeFragment,
        val unrelatedCodeFragment: KtBlockCodeFragment,

        val contextModuleTracker: ModuleModificationEventTracker,
        val unrelatedModuleTracker: ModuleModificationEventTracker,
        val codeFragmentTracker: ModuleModificationEventTracker,
        val nestedCodeFragmentTracker: ModuleModificationEventTracker,
        val unrelatedCodeFragmentTracker: ModuleModificationEventTracker,
    )

    private fun createModuleStructure(): SharedCodeFragmentModuleStructure {
        val contextModule = createModuleInTmpDir("contextModule") {
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
        val contextModuleSourceFile = contextModule.findSourceKtFile("test.kt")

        val unrelatedModule = createModuleInTmpDir("unrelatedModule") {
            val srcFile = FileWithText(
                "unrelated.kt",
                """
                    fun unrelated() {
                        val y = 1
                    }
                """.trimIndent()
            )

            listOf(srcFile)
        }
        val unrelatedModuleSourceFile = unrelatedModule.findSourceKtFile("unrelated.kt")

        val barCall = contextModuleSourceFile.findDescendantOfType<KtCallExpression> { it.text == "bar()" }!!

        val codeFragment = KtBlockCodeFragment(project, "codeFragment.kt", "bar<Int>()", "", barCall)

        val codeFragmentBarCall = codeFragment.findDescendantOfType<KtCallExpression> { it.text == "bar<Int>()" }
        val nestedCodeFragment = KtBlockCodeFragment(project, "nestedCodeFragment.kt", "bar<String>()", "", codeFragmentBarCall)

        val unrelatedCodeFragment = KtBlockCodeFragment(project, "unrelatedCodeFragment.kt", "bar<Double>()", "", barCall)

        // If a file is unresolved, in-block modification events such as `CODE_FRAGMENT_CONTEXT_MODIFICATION` won't trigger because we won't
        // have any caches for it that would need to be invalidated.
        ensureFilesResolved(contextModuleSourceFile, unrelatedModuleSourceFile, codeFragment, nestedCodeFragment, unrelatedCodeFragment)

        return SharedCodeFragmentModuleStructure(
            contextModuleSourceFile,
            unrelatedModuleSourceFile,
            codeFragment,
            nestedCodeFragment,
            unrelatedCodeFragment,

            createTracker(contextModule, "context module"),
            createTracker(unrelatedModule, "unrelated module"),
            createTracker(codeFragment, "code fragment"),
            createTracker(nestedCodeFragment, "nested code fragment"),
            createTracker(unrelatedCodeFragment, "unrelated code fragment"),
        )
    }
}
