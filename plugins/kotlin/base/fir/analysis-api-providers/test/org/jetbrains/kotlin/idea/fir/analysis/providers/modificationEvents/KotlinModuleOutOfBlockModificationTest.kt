// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.topics.KotlinModuleOutOfBlockModificationListener
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.*
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
            typeHelloInSingleFunctionBody(textAfterTyping = "fun main() = hello10")
        }

        // We expect two events: One published before and one after the PSI tree modification.
        trackerA.assertModified("module A with out-of-block change after typing", expectedEventCount = 2)
        trackerB.assertNotModified("unmodified module B")
        trackerC.assertNotModified("unmodified module C")

        disposeTrackers(trackerA, trackerB, trackerC)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA)
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

        disposeTrackers(trackerA, trackerB)
    }

    private fun KtFile.configureEditor() {
        configureByExistingFile(virtualFile)
    }

    private fun Module.configureEditorForFile(fileName: String): KtFile {
        val file = "${sourceRoots.first().url}/$fileName"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(myProject).findFile(virtualFile) as KtFile
        ktFile.configureEditor()
        return ktFile
    }

    private fun KtFile.modify(textAfterModification: String, targetOffset: Int? = null, edit: () -> Unit) {
        targetOffset?.let(editor.caretModel::moveToOffset)
        edit()
        PsiDocumentManager.getInstance(myProject).commitAllDocuments()
        Assert.assertEquals(textAfterModification, text)
    }

    private fun KtFile.typeHelloInSingleFunctionBody(textAfterTyping: String) {
        modify(textAfterTyping, getSingleFunctionBodyOffset()) {
            type("hello")
        }
    }

    private fun KtFile.getSingleFunctionBodyOffset(): Int {
        val singleFunction = declarations.single() as KtNamedFunction
        return singleFunction.bodyExpression!!.textOffset
    }

    private fun KtFile.getSingleMemberInClassOffset(): Int {
        val singleMember = (declarations[0] as KtClass).declarations.single()
        return singleMember.textRange.startOffset
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
