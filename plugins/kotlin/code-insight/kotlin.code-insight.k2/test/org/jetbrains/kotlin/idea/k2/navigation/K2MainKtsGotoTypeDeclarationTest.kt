// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.k2.AbstractScriptGotoDeclarationMultifileTest
import org.jetbrains.kotlin.idea.k2.EXPECTED_TEXT
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.Assertions
import kotlin.io.path.Path

class K2MainKtsGotoTypeDeclarationTest : AbstractScriptGotoDeclarationMultifileTest() {
    fun testImportedScriptCallableReturnType() {
        doTest(Path(testDataDirectory.path, "idea/tests/testData/mainKts/navigation/gotoTypeDeclaration/importedScriptReturnType.test").toString())
    }

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val expectedText = globalDirectives[EXPECTED_TEXT]
        Assertions.assertNotNull(expectedText, "$EXPECTED_TEXT directive not found")
        expectedText ?: return

        val mainFile = files.first() as KtFile

        runInEdtAndWait {
            myFixture.configureFromExistingVirtualFile(mainFile.virtualFile)
        }

        runBlocking {
            KotlinScriptService.getInstance(project).load(mainFile.virtualFile)
        }

        runInEdtAndWait {
            myFixture.performEditorAction(IdeActions.ACTION_GOTO_TYPE_DECLARATION)
        }

        Assertions.assertTrue(document.text.contains(expectedText), "Actual text:\n\n${document.text}")
    }
}
