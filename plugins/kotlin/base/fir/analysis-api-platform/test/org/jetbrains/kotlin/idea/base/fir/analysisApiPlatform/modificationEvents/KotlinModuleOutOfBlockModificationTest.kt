// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.impl.ModuleRootEventImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.junit.Assert

class KotlinModuleOutOfBlockModificationTest : AbstractKotlinModuleModificationEventTest() {
    override val expectedEventKind: KotlinModificationEventKind
        get() = KotlinModificationEventKind.MODULE_OUT_OF_BLOCK_MODIFICATION

    fun `test that source module out-of-block modification affects a single module`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() = 10")
            )
        }
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA, "module A after an out-of-block modification")
        val trackerB = createTracker(moduleB, "module B")
        val trackerC = createTracker(moduleC, "module C")

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "fun main() = hello10", getSingleFunctionBodyOffset()) {
                type("hello")
            }
        }

        trackerA.assertModified()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
    }

    fun `test that source module out-of-block modification does not occur after deleting a symbol in a function body`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    "fun main() {\n" +
                            "val v = <caret>\n" +
                            "}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after an in-block modification")

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "fun main() {\n" + "val v =\n" + "}") {
                backspace()
            }
        }

        trackerA.assertNotModified()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test module invalidation in code fragments`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """fun main() {
                        val a = 0
                        <caret>a
                    }""".trimIndent()
                )
            )
        }

        val referenceExpression =
            moduleA.configureEditorForFile("main.kt").findElementAt(editor.caretModel.offset)!!.parentOfType<KtNameReferenceExpression>()!!

        val factory = KtPsiFactory.contextual(referenceExpression)
        val contentElement = factory.createExpressionCodeFragment("2", referenceExpression).getContentElement() as KtExpression

        allowAnalysisOnEdt {
            // build fir for context and code fragment
            analyze(referenceExpression) {
                referenceExpression.expressionType
            }
            analyze(contentElement) {
                contentElement.expressionType
            }
        }

        //invalidate code fragment context
        project.executeWriteCommand("replace", null) {
            referenceExpression.parentOfType<KtFunction>()!!.replace(factory.createFunction("fun foo() {}"))
        }

        //ensure to drop all cached values,
        //especially for [org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderIdeImplKt.cachedKtModule]
        project.executeWriteCommand("roots changed", null) {
            val publisher = project.messageBus.syncPublisher(ModuleRootListener.TOPIC)
            val rootChangedEvent = ModuleRootEventImpl(project, false)
            publisher.beforeRootsChange(rootChangedEvent)
            publisher.rootsChanged(rootChangedEvent)
        }

        //on finish writeAction `FirIdeOutOfBlockPsiTreeChangePreprocessor.treeChanged` will collect data
        project.executeWriteCommand("doc change", null) {
            contentElement.replace(factory.createExpression("42"))
        }
        //as listener on finish of write action, LLFirDeclarationModificationService.processQueue will proceed with fragment with invalid context

        assertFalse(contentElement.isValid)
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test module invalidation in code fragments, first fragment modification`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """fun main() {
                        val a = 0
                        <caret>a
                    }""".trimIndent()
                )
            )
        }

        val referenceExpression =
            moduleA.configureEditorForFile("main.kt").findElementAt(editor.caretModel.offset)!!.parentOfType<KtNameReferenceExpression>()!!

        val factory = KtPsiFactory.contextual(referenceExpression)
        val contentElement = factory.createExpressionCodeFragment("2", referenceExpression).getContentElement() as KtExpression

        allowAnalysisOnEdt {
            // build fir for context and code fragment
            analyze(referenceExpression) {
                referenceExpression.expressionType
            }
            analyze(contentElement) {
                contentElement.expressionType
            }
        }

        project.executeWriteCommand("replace", null) {
            // ensure special module is not cached
            val publisher = project.messageBus.syncPublisher(ModuleRootListener.TOPIC)
            val rootChangedEvent = ModuleRootEventImpl(project, false)
            publisher.beforeRootsChange(rootChangedEvent)
            publisher.rootsChanged(rootChangedEvent)

            //perform in block modification in code fragment, sync treeChanged stores owner in the queue
            contentElement.replace(factory.createExpression("42"))
            //invalidate context
            referenceExpression.parentOfType<KtFunction>()!!.replace(factory.createFunction("fun foo() {}"))
        }
        //as listener on finish of write action, LLFirDeclarationModificationService.processQueue will proceed with fragment with invalid context

        assertFalse(contentElement.isValid)
    }

    fun `test that source module out-of-block modification does not occur after deleting whitespace`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    "class Main {    fun main() {}\n}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after deleting whitespace")

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "class Main {   fun main() {}\n}", getSingleMemberInClassOffset()) {
                backspace()
            }
        }

        trackerA.assertNotModified()
    }

    fun `test that source module out-of-block modification occurs after adding a space in an identifier`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    "class Main {    fun main() {}\n}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after adding a space in an identifier")

        moduleA.configureEditorForFile("main.kt").apply {
            modify(
                textAfterModification = "class Main {    fun m ain() {}\n}",
                getSingleMemberInClassOffset() + "fun m".length,
            ) {
                type(" ")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after commenting out a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {    fun main() {}\n}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after commenting out a function")

        moduleA.configureEditorForFile("main.kt").apply {
            modify("class Main {    //fun main() {}\n}", getSingleMemberInClassOffset()) {
                type("//")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification does not occur after commenting out a type inside a function body`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    "class Main {\n" +
                            "    fun main() {\n" +
                            "      class Local {}\n" +
                            "    }\n" +
                            "}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after commenting out a type inside a function body")

        moduleA.configureEditorForFile("main.kt").apply {
            val textAfterModification =
                "class Main {\n" +
                    "    fun main() {\n" +
                    "//      class Local {}\n" +
                    "    }\n" +
                    "}"

            val singleFunction = (declarations[0] as KtClass).declarations.single() as KtFunction
            val offset = singleFunction.bodyBlockExpression?.lBrace?.textOffset!! + 2

            modify(textAfterModification, offset) {
                type("//")
            }
        }

        trackerA.assertNotModified()
    }

    fun `test that source module out-of-block modification occurs after commenting out a type`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after commenting out a type")

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "//class Main {}", 0) {
                type("//")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after adding a modifier to a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "<caret>inline fun main() {}"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after adding a modifier to a function")

        moduleA.configureEditorForFile("main.kt").apply {
            modify("private inline fun main() {}") {
                type("private ")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after adding a return type to a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "fun foo()<caret> = 10"
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after adding a return type to a function")

        moduleA.configureEditorForFile("main.kt").apply {
            modify("fun foo(): Byte = 10") {
                type(": Byte")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after adding a contract to a function body`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """
                        inline fun foo(block: () -> Unit) {
                            <caret>block()
                        }
                    """.trimIndent()
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after adding a contract to a function body")

        val textAfterModification =
            """
                inline fun foo(block: () -> Unit) {
                    kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                    block()
                }
            """.trimIndent()

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification) {
                type("kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }\n")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after deleting a contract inside a function body`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """
                        inline fun foo(block: () -> Unit) {
                            <caret>kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                            block()
                        }
                    """.trimIndent()
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after deleting a contract inside a function body")

        val textAfterModification =
            """
                inline fun foo(block: () -> Unit) {
                    block()
                }
            """.trimIndent()

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification) {
                deleteLine()
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after wrapping a contract statement in an if-expression`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """
                        inline fun foo(block: () -> Unit) {
                            <caret>kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                            block()
                        }
                    """.trimIndent()
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after wrapping a contract statement in an if-expression")

        val textAfterModification =
            """
                inline fun foo(block: () -> Unit) {
                    if (1 == 2) kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                    block()
                }
            """.trimIndent()

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification) {
                type("if (1 == 2) ")
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification occurs after unwrapping an illegally nested contract statement`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt",
                    """
                        inline fun foo(block: () -> Unit) {
                            <caret>if (1 == 2)
                            kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                            block()
                        }
                    """.trimIndent()
                )
            )
        }

        val trackerA = createTracker(moduleA, "module A after unwrapping an illegally nested contract statement")

        val textAfterModification =
            """
                inline fun foo(block: () -> Unit) {
                    kotlin.contracts.contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
                    block()
                }
            """.trimIndent()

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification) {
                deleteLine()
            }
        }

        trackerA.assertModified()
    }

    fun `test that source module out-of-block modification does not occur after changing a non-physical file`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() {}")
            )
        }
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA, "module A")
        val trackerB = createTracker(moduleB, "module B")

        runWriteAction {
            val nonPhysicalPsi = KtPsiFactory(moduleA.project).createFile("nonPhysical", "val a = c")
            nonPhysicalPsi.add(KtPsiFactory(moduleA.project).createFunction("fun x(){}"))
        }

        trackerA.assertNotModified()
        trackerB.assertNotModified()
    }

    fun `test that script module out-of-block modification affects a single script module`() {
        val scriptA = createScript("a", "fun foo() = 10")
        val scriptB = createScript("b", "fun bar() = 10")
        val moduleC = createModuleInTmpDir("c")
        val fileD = createNotUnderContentRootFile("d", "fun baz() = 10")

        val allowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)
        val trackerA = createTracker(scriptA, "script A after an out-of-block modification", allowedEventKinds)
        val trackerB = createTracker(scriptB, "script B", allowedEventKinds)
        val trackerC = createTracker(moduleC, "module C", allowedEventKinds)
        val trackerD = createTracker(fileD, "not-under-content-root file D", allowedEventKinds)

        scriptA.withConfiguredEditor {
            val singleFunction = (declarations.single() as KtScript).declarations.single() as KtNamedFunction
            val offset = singleFunction.bodyExpression!!.startOffset
            modify(textAfterModification = "fun foo() = hello10", offset) {
                type("hello")
            }
        }

        trackerA.assertModified()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    fun `test that not-under-content-root module out-of-block modification affects a single not-under-content-root module`() {
        val fileA = createNotUnderContentRootFile("a", "fun foo() = 10")
        val fileB = createNotUnderContentRootFile("b", "fun bar() = 10")
        val moduleC = createModuleInTmpDir("c")
        val scriptD = createScript("d", "fun baz() = 10")

        val allowedEventKinds = setOf(KotlinModificationEventKind.GLOBAL_MODULE_STATE_MODIFICATION)
        val trackerA = createTracker(fileA, "not-under-content-root file A after an out-of-block modification", allowedEventKinds)
        val trackerB = createTracker(fileB, "not-under-content-root file B", allowedEventKinds)
        val trackerC = createTracker(moduleC, "module C", allowedEventKinds)
        val trackerD = createTracker(scriptD, "module D", allowedEventKinds)

        fileA.withConfiguredEditor {
            modify(textAfterModification = "fun foo() = hello10", getSingleFunctionBodyOffset()) {
                type("hello")
            }
        }

        trackerA.assertModified()
        trackerB.assertNotModified()
        trackerC.assertNotModified()
        trackerD.assertNotModified()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun `test code fragment out-of-block modification does not happen after body modification`() {
        val contextModule = createModuleInTmpDir("ctx") {
            val contextFile = FileWithText(
                "context.kt",
                """
                    fun main() {
                        val x = 0
                    }
                """.trimIndent()
            )

            listOf(contextFile)
        }

        val contextTracker = createTracker(contextModule, "unmodified context module")

        contextModule.configureEditorForFile("context.kt").apply {
            val contextCall = findDescendantOfType<KtVariableDeclaration> { it.name == "x" }

            val codeFragment = KtExpressionCodeFragment(project, "fragment.kt", "secondary()", imports = null, contextCall)
            assert(codeFragment.viewProvider.isEventSystemEnabled)

            val codeFragmentModule = KotlinProjectStructureProvider.getModule(project, codeFragment, useSiteModule = null)
            val codeFragmentTracker = createTracker(
                codeFragmentModule,
                "code fragment",

                // Any in-block modification may cause a code fragment context modification event for the modified module. This is expected
                // for a dangling file module as well, because it may be a context module of another dangling file module. Hence, we need to
                // allow `CODE_FRAGMENT_CONTEXT_MODIFICATION`.
                //
                // Note that we don't allow a code fragment context modification event for the context module, which would indeed be wrong.
                additionalAllowedEventKinds = setOf(KotlinModificationEventKind.CODE_FRAGMENT_CONTEXT_MODIFICATION),
            )

            allowAnalysisOnEdt {
                analyze(codeFragment) {
                    val callExpression = codeFragment.findDescendantOfType<KtCallExpression>() ?: error("Replaced call is not found")
                    val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull()
                    assert(resolvedCall == null)
                }
            }

            runWriteAction {
                executeCommand(project) {
                    val newContentElement = KtPsiFactory(project).createExpression("main()")
                    codeFragment.getContentElement()!!.replace(newContentElement)
                }
            }

            codeFragmentTracker.assertNotModified()

            allowAnalysisOnEdt {
                analyze(codeFragment) {
                    val callExpression = codeFragment.findDescendantOfType<KtCallExpression>() ?: error("Replaced call is not found")
                    val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull()
                    val resolvedFunction = resolvedCall?.symbol as? KaNamedFunctionSymbol
                    assert(resolvedFunction != null && resolvedFunction.name.asString() == "main" )
                }
            }
        }

        contextTracker.assertNotModified()
    }

    fun `test code fragment out-of-block modification happens after import insertion`() {
        val contextModule = createModuleInTmpDir("ctx") {
            val contextFile = FileWithText(
                "context.kt",
                """
                    fun main() {
                        val x = 0
                    }
                """.trimIndent()
            )

            listOf(contextFile)
        }

        val contextTracker = createTracker(contextModule, "unmodified context module")

        contextModule.configureEditorForFile("context.kt").apply {
            val contextCall = findDescendantOfType<KtVariableDeclaration> { it.name == "x" }

            val codeFragment = KtExpressionCodeFragment(project, "fragment.kt", "File(\"\")", imports = null, contextCall)
            assert(codeFragment.viewProvider.isEventSystemEnabled)

            val codeFragmentTracker = createTracker(codeFragment, "code fragment")

            runWriteAction {
                executeCommand(project) {
                    codeFragment.addImportsFromString("java.io.File")
                }
            }

            codeFragmentTracker.assertModifiedOnce()
        }

        contextTracker.assertNotModified()
    }

    private fun KtFile.configureEditor() {
        configureByExistingFile(virtualFile)
    }

    private fun KtFile.withConfiguredEditor(f: KtFile.() -> Unit) {
        configureEditor()
        f()
    }

    private fun Module.configureEditorForFile(fileName: String): KtFile = findSourceKtFile(fileName).apply { configureEditor() }

    private fun KtFile.modify(textAfterModification: String, targetOffset: Int? = null, edit: () -> Unit) {
        targetOffset?.let(editor.caretModel::moveToOffset)
        edit()
        PsiDocumentManager.getInstance(myProject).commitAllDocuments()
        Assert.assertEquals(textAfterModification, text)
    }

    private fun KtFile.getSingleFunctionBodyOffset(): Int {
        val singleFunction = declarations.single() as KtNamedFunction
        return singleFunction.bodyExpression!!.textOffset
    }

    private fun KtFile.getSingleMemberInClassOffset(): Int {
        val singleMember = (declarations[0] as KtClass).declarations.single()
        return singleMember.startOffset
    }
}
