// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.safeDelete

import com.google.gson.JsonObject
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.rename.loadTestConfiguration
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinMultiFileTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import java.io.File

abstract class AbstractMultiModuleSafeDeleteTest : KotlinMultiFileTestCase() {

    object SafeDeleteAction : AbstractMultifileRefactoringTest.RefactoringAction {
        override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
            @Suppress("UNCHECKED_CAST")
            val elementClass = Class.forName(config.getString("elementClass")) as Class<PsiElement>
            val element = elementsAtCaret.single().getNonStrictParentOfType(elementClass)!!
            val project = mainFile.project
            with (KotlinSafeDeleteSettings) {
                project.ALLOW_LIFTING_ACTUAL_PARAMETER_TO_EXPECTED = config.get("liftParameterToExpected")?.asBoolean ?: true
            }
            SafeDeleteHandler.invoke(project, arrayOf(element), null, true, null)
        }
    }

    override fun compareResults(rootAfter: VirtualFile, rootDir: VirtualFile) {
        PlatformTestUtil.assertDirectoriesEqual(rootAfter, rootDir, ::fileFilter, ::fileNameMapper)
    }

    override fun fileFilter(file: VirtualFile): Boolean {
        if (file.isFile && file.extension == "kt") {
            if (file.name.endsWith(".k2.kt")) return false
        }
        return !KotlinTestUtils.isMultiExtensionName(file.name)
    }

    override fun fileNameMapper(file: VirtualFile): String =
        file.name.replace(".k2.kt", ".kt")

    override fun getTestRoot(): String = "/refactoring/safeDeleteMultiModule/"
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR

    protected open fun getAlternativeConflictsFile(): String? = null

    fun doTest(path: String) {
        val config = loadTestConfiguration(File(path))

        isMultiModule = true

        val isEnabled = config.get("enabledIn${pluginMode.name}")?.asBoolean != false

        val results = runCatching {
            doTestCommittingDocuments { rootDir, _ ->
                runRefactoringTest(path, config, rootDir, project, SafeDeleteAction, getAlternativeConflictsFile())
            }
        }

        results.fold(
            onSuccess = { require(isEnabled) { "This test passes and should be enabled!" } },
            onFailure = { exception -> if (isEnabled) throw exception }
        )
    }
}