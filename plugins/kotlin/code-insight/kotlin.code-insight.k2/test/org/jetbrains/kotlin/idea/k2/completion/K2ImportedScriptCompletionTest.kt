// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.completion

import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.k2.AbstractScriptGotoDeclarationMultifileTest
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.Assertions
import kotlin.io.path.Path

/**
 * Verifies that public top-level declarations of scripts imported via the script configuration's imported-scripts
 * key are offered in ordinary completion, for any script definition. The platform's callable completion only
 * surfaces these (script-class member) declarations on repeated explicit invocation; this exercises
 * [org.jetbrains.kotlin.idea.core.script.k2.codeInsight.KotlinImportedScriptCompletionProvider].
 *
 * Each test is data-driven by a multifile `.test`; the first file is completed at `<caret>` and the
 * `// EXIST:` / `// ABSENT:` directives assert presence/absence of lookup strings.
 */
class K2ImportedScriptCompletionTest : AbstractScriptGotoDeclarationMultifileTest() {
    fun testImportedScriptSymbols() = doTest(testDataPath("importedScriptSymbols.test"))

    fun testTransitiveImportedScripts() = doTest(testDataPath("transitiveImportedScripts.test"))

    private fun testDataPath(fileName: String): String =
        Path(testDataDirectory.path, "idea/tests/testData/script/completion/importedScripts/$fileName").toString()

    override fun doMultiFileTest(files: List<PsiFile>, globalDirectives: Directives) {
        val mainFile = files.first() as KtFile

        runInEdtAndWait { myFixture.configureFromExistingVirtualFile(mainFile.virtualFile) }
        runBlocking { KotlinScriptService.getInstance(project).load(mainFile.virtualFile) }

        runInEdtAndWait {
            myFixture.completeBasic()
            val lookups = myFixture.lookupElementStrings.orEmpty()
            globalDirectives.listValues("EXIST")?.forEach {
                Assertions.assertTrue(it in lookups, "Expected '$it' in completion, got: $lookups")
            }
            globalDirectives.listValues("ABSENT")?.forEach {
                Assertions.assertFalse(it in lookups, "Did not expect '$it' in completion, got: $lookups")
            }
        }
    }
}
