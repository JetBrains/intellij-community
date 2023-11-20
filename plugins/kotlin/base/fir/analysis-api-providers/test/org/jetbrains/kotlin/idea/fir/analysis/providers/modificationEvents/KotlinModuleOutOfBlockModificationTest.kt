// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.junit.Assert

class KotlinModuleOutOfBlockModificationTest : AbstractKotlinModuleModificationEventTest<ModuleOutOfBlockModificationEventTracker>() {
    override fun constructTracker(module: KtModule): ModuleOutOfBlockModificationEventTracker = ModuleOutOfBlockModificationEventTracker(module)

    fun `test that source module out-of-block modification affects a single module`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() = 10")
            )
        }
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)
        val trackerC = createTracker(moduleC)

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "fun main() = hello10", getSingleFunctionBodyOffset()) {
                type("hello")
            }
        }

        // We expect two events: One published before and one after the PSI tree modification.
        trackerA.assertModified("module A after out-of-block change", expectedEventCount = 2)
        trackerB.assertNotModified("unmodified module B")
        trackerC.assertNotModified("unmodified module C")
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

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "fun main() {\n" + "val v =\n" + "}") {
                backspace()
            }
        }

        trackerA.assertNotModified("module A with an in-block modification")
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

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "class Main {   fun main() {}\n}", getSingleMemberInClassOffset()) {
                backspace()
            }
        }

        trackerA.assertNotModified("module A after deleting whitespace")
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

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify(
                textAfterModification = "class Main {    fun m ain() {}\n}",
                getSingleMemberInClassOffset() + "fun m".length,
            ) {
                type(" ")
            }
        }

        // In total, seven PSI tree changes are processed: Two `BEFORE_CHILD_REPLACEMENT`, one `CHILDREN_CHANGED`, and four `CHILD_ADDED`.
        trackerA.assertModified("module A after adding a space in an identifier", expectedEventCount = 7)
    }

    fun `test that source module out-of-block modification occurs after commenting out a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {    fun main() {}\n}"
                )
            )
        }

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify("class Main {    //fun main() {}\n}", getSingleMemberInClassOffset()) {
                type("//")
            }
        }

        trackerA.assertModifiedOnce("module A after commenting out a function")
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

        val trackerA = createTracker(moduleA)

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

        trackerA.assertNotModified("module A after commenting out a type inside a function body")
    }

    fun `test that source module out-of-block modification occurs after commenting out a type`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {}"
                )
            )
        }

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify(textAfterModification = "//class Main {}", 0) {
                type("//")
            }
        }

        trackerA.assertModifiedOnce("module A after commenting out a type")
    }

    fun `test that source module out-of-block modification occurs after adding a modifier to a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "<caret>inline fun main() {}"
                )
            )
        }

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify("private inline fun main() {}") {
                type("private ")
            }
        }

        // In total, seven PSI tree changes are processed: Three `BEFORE_CHILD_REMOVAL`, three `CHILD_REMOVED`, and one `CHILD_ADDED`.
        trackerA.assertModified("module A after adding a modifier to a function", expectedEventCount = 7)
    }

    fun `test that source module out-of-block modification occurs after adding a return type to a function`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "fun foo()<caret> = 10"
                )
            )
        }

        val trackerA = createTracker(moduleA)

        moduleA.configureEditorForFile("main.kt").apply {
            modify("fun foo(): Byte = 10") {
                type(": Byte")
            }
        }

        // In total, seventeen PSI tree changes are processed: Five `BEFORE_CHILD_REMOVAL`, two `CHILDREN_CHANGED`, seven `CHILD_REMOVED`,
        // two `CHILD_ADDED`, and one `BEFORE_CHILD_REPLACEMENT`.
        trackerA.assertModified("module A after adding a return type to a function", expectedEventCount = 17)
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

        val trackerA = createTracker(moduleA)

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

        trackerA.assertModified("module A after adding a contract to a function body", expectedEventCount = 15)
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

        val trackerA = createTracker(moduleA)

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

        trackerA.assertModified("module A after deleting a contract inside a function body", expectedEventCount = 2)
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

        val trackerA = createTracker(moduleA)

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

        trackerA.assertModified("module A after wrapping a contract statement in an if-expression", expectedEventCount = 2)
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

        val trackerA = createTracker(moduleA)

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

        trackerA.assertModifiedOnce("module A after unwrapping an illegally nested contract statement")
    }

    fun `test that source module out-of-block modification does not occur after changing a non-physical file`() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() {}")
            )
        }
        val moduleB = createModuleInTmpDir("b")

        val trackerA = createTracker(moduleA)
        val trackerB = createTracker(moduleB)

        runWriteAction {
            val nonPhysicalPsi = KtPsiFactory(moduleA.project).createFile("nonPhysical", "val a = c")
            nonPhysicalPsi.add(KtPsiFactory(moduleA.project).createFunction("fun x(){}"))
        }

        trackerA.assertNotModified("module A")
        trackerB.assertNotModified("module B")
    }

    fun `test that script module out-of-block modification affects a single script module`() {
        val scriptA = createScript("a", "fun foo() = 10")
        val scriptB = createScript("b", "fun bar() = 10")
        val moduleC = createModuleInTmpDir("c")
        val fileD = createNotUnderContentRootFile("d", "fun baz() = 10")

        val trackerA = createTracker(scriptA)
        val trackerB = createTracker(scriptB)
        val trackerC = createTracker(moduleC)
        val trackerD = createTracker(fileD)

        scriptA.withConfiguredEditor {
            val singleFunction = (declarations.single() as KtScript).declarations.single() as KtNamedFunction
            val offset = singleFunction.bodyExpression!!.startOffset
            modify(textAfterModification = "fun foo() = hello10", offset) {
                type("hello")
            }
        }

        // We expect two events: One published before and one after the PSI tree modification.
        trackerA.assertModified("script A after out-of-block modification", expectedEventCount = 2)
        trackerB.assertNotModified("unmodified script B")
        trackerC.assertNotModified("unmodified module C")
        trackerD.assertNotModified("unmodified not-under-content-root file D")
    }

    fun `test that not-under-content-root module out-of-block modification affects a single not-under-content-root module`() {
        val fileA = createNotUnderContentRootFile("a", "fun foo() = 10")
        val fileB = createNotUnderContentRootFile("b", "fun bar() = 10")
        val moduleC = createModuleInTmpDir("c")
        val scriptD = createScript("d", "fun baz() = 10")

        val trackerA = createTracker(fileA)
        val trackerB = createTracker(fileB)
        val trackerC = createTracker(moduleC)
        val trackerD = createTracker(scriptD)

        fileA.withConfiguredEditor {
            modify(textAfterModification = "fun foo() = hello10", getSingleFunctionBodyOffset()) {
                type("hello")
            }
        }

        // We expect two events: One published before and one after the PSI tree modification.
        trackerA.assertModified("not-under-content-root file A after out-of-block modification", expectedEventCount = 2)
        trackerB.assertNotModified("unmodified not-under-content-root file B")
        trackerC.assertNotModified("unmodified module C")
        trackerD.assertNotModified("unmodified script D")
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

class ModuleOutOfBlockModificationEventTracker(module: KtModule) : ModuleModificationEventTracker(
    module,
    eventKind = "module out-of-block modification",
) {
    override fun configureSubscriptions(busConnection: MessageBusConnection) {
        busConnection.subscribe(
            KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION,
            KotlinModuleOutOfBlockModificationListener(::handleEvent),
        )
    }
}
