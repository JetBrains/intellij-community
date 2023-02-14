// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.analysis.providers.trackers

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.providers.createModuleWithoutDependenciesOutOfBlockModificationTracker
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.*
import org.junit.Assert
import java.io.File

class KotlinModuleOutOfBlockTrackerTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override fun isFirPlugin(): Boolean = true

    fun testThatModuleOutOfBlockChangeInfluenceOnlySingleModule() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() = 10")
            )
        }
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")


        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)
        val moduleBWithTracker = ModuleWithModificationTracker(moduleB)
        val moduleCWithTracker = ModuleWithModificationTracker(moduleC)

        moduleA.typeInFunctionBody("main.kt", textAfterTyping = "fun main() = hello10")

        Assert.assertTrue(
            "Out of block modification count for module A with out of block should change after typing, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Out of block modification count for module B without out of block should not change after typing, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertFalse(
            "Out of block modification count for module C without out of block should not change after typing, modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatDeleteSymbolInBodyDoesNotLeadToOutOfBlockChange() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "fun main() {\n" +
                            "val v = <caret>\n" +
                            "}"
                )
            )
        }

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)

        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(moduleA.project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)
        backspace()
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertFalse(
            "Out of block modification count for module A should not change after deleting, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertEquals("fun main() {\n" +
                                    "val v =\n" + 
                                    "}", ktFile.text)
    }

    //1. outside function
    //2. in function identifier
    fun testWhitespace() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {" +
                            "    fun main() {}\n" +
                            "}"
                )
            )
        }

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)

        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(moduleA.project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)
        val singleFunction = (ktFile.declarations[0] as KtClass).declarations.single()
        val startOffset = singleFunction.textRange.startOffset
        editor.caretModel.moveToOffset(startOffset)
        backspace()
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertFalse(
            "Out of block modification count for module A should not change after deleting, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertEquals("class Main {   fun main() {}\n" +
                                    "}", ktFile.text)

        editor.caretModel.moveToOffset(startOffset + "fun ".length)
        type(" ")
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()
        Assert.assertTrue(
            "Out of block modification count for module A should change after adding space in identifier, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertEquals("class Main {   fun m ain() {}\n" +
                                    "}", ktFile.text)

    }

    fun testCommentFunction() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {" +
                            "    fun main() {}\n" +
                            "}"
                )
            )
        }

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)

        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(moduleA.project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)
        val singleFunction = (ktFile.declarations[0] as KtClass).declarations.single()
        val startOffset = singleFunction.textRange.startOffset
        editor.caretModel.moveToOffset(startOffset)
        type("//")
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertTrue(
            "Out of block modification count for module A should change after commenting, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
    }

    fun testLocalCommentType() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {\n" +
                            "    fun main() {\n" +
                            "      class Local {}\n" +
                            "    }\n" +
                            "}"
                )
            )
        }

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)

        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(moduleA.project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)
        val singleFunction = (ktFile.declarations[0] as KtClass).declarations.single() as KtFunction
        val startOffset = (singleFunction.bodyBlockExpression?.lBrace?.textOffset ?: 0) + 2
        editor.caretModel.moveToOffset(startOffset)
        type("//")
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertFalse(
            "Out of block modification count for module A should not change after commenting, local classes are not available outside of the method," +
                    "modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
    }

    fun testCommentType() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "class Main {}"
                )
            )
        }
        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)
        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        configureByExistingFile(virtualFile)
        editor.caretModel.moveToOffset(0)
        type("//")
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertTrue(
            "Out of block modification count for module A should change after commenting, local classes are not available outside of the method," +
                    "modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
    }
    
    fun testThatAddModifierDoesLeadToOutOfBlockChange() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText(
                    "main.kt", "<caret>inline fun main() {}"
                )
            )
        }

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)

        val file = "${moduleA.sourceRoots.first().url}/${"main.kt"}"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(moduleA.project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)
        type("private ")
        PsiDocumentManager.getInstance(moduleA.project).commitAllDocuments()

        Assert.assertTrue(
            "Out of block modification count for module A should be changed after specifying return type, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertEquals("private inline fun main() {}", ktFile.text)
    }

    fun testThatInEveryModuleOutOfBlockWillHappenAfterContentRootChange() {
        val moduleA = createModuleInTmpDir("a")
        val moduleB = createModuleInTmpDir("b")
        val moduleC = createModuleInTmpDir("c")

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)
        val moduleBWithTracker = ModuleWithModificationTracker(moduleB)
        val moduleCWithTracker = ModuleWithModificationTracker(moduleC)

        runWriteAction {
            moduleA.sourceRoots.first().createChildData(/* requestor = */ null, "file.kt")
        }

        Assert.assertTrue(
            "Out of block modification count for module A should change after content root change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertTrue(
            "Out of block modification count for module B should change after content root change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )
        Assert.assertTrue(
            "Out of block modification count for module C should change after content root change modification count is ${moduleCWithTracker.modificationCount}",
            moduleCWithTracker.changed()
        )
    }

    fun testThatNonPhysicalFileChangeNotCausingBOOM() {
        val moduleA = createModuleInTmpDir("a") {
            listOf(
                FileWithText("main.kt", "fun main() {}")
            )
        }
        val moduleB = createModuleInTmpDir("b")

        val moduleAWithTracker = ModuleWithModificationTracker(moduleA)
        val moduleBWithTracker = ModuleWithModificationTracker(moduleB)


        val projectWithModificationTracker = ProjectWithModificationTracker(project)

        runWriteAction {
            val nonPhysicalPsi = KtPsiFactory(moduleA.project).createFile("nonPhysical", "val a = c")
            nonPhysicalPsi.add(KtPsiFactory(moduleA.project).createFunction("fun x(){}"))
        }

        Assert.assertFalse(
            "Out of block modification count for module A should not change after non physical file change, modification count is ${moduleAWithTracker.modificationCount}",
            moduleAWithTracker.changed()
        )
        Assert.assertFalse(
            "Out of block modification count for module B should not change after non physical file change, modification count is ${moduleBWithTracker.modificationCount}",
            moduleBWithTracker.changed()
        )

        Assert.assertFalse(
            "Out of block modification count for project should not change after non physical file change, modification count is ${projectWithModificationTracker.modificationCount}",
            projectWithModificationTracker.changed()
        )
    }

    private fun Module.typeInFunctionBody(fileName: String, textAfterTyping: String) {
        val file = "${sourceRoots.first().url}/$fileName"
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file)!!
        val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as KtFile
        configureByExistingFile(virtualFile)

        val singleFunction = ktFile.declarations.single() as KtNamedFunction

        editor.caretModel.moveToOffset(singleFunction.bodyExpression!!.textOffset)
        type("hello")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        Assert.assertEquals(textAfterTyping, ktFile.text)
    }

    abstract class WithModificationTracker(private val modificationTracker: ModificationTracker) {
        private val initialModificationCount = modificationTracker.modificationCount
        val modificationCount: Long get() = modificationTracker.modificationCount

        fun changed(): Boolean =
            modificationTracker.modificationCount != initialModificationCount
    }

    private class ModuleWithModificationTracker(module: Module) : WithModificationTracker(
        module.getMainKtSourceModule()!!.createModuleWithoutDependenciesOutOfBlockModificationTracker(module.project)
    )

    private class ProjectWithModificationTracker(project: Project) : WithModificationTracker(
        project.createProjectWideOutOfBlockModificationTracker()
    )
}