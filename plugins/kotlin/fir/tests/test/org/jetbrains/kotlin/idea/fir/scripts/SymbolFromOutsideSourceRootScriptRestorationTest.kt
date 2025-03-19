// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.scripts

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class SymbolFromOutsideSourceRootScriptRestorationTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2

    override fun runInDispatchThread(): Boolean = false

    fun test() = runBlocking {
        val scriptFile = createScriptFileInTempDirectory()

        val ktFunction = readAction { scriptFile.findDescendantOfType<KtNamedFunction>() }
            ?: error("Cannot find function in ${scriptFile.text}")

        val pointer = readAction {
            analyze(ktFunction) {
                ktFunction.symbol.createPointer()
            }
        }

        val restoredName = readAction {
            analyze(ktFunction) {
                pointer.restoreSymbol()?.name
            }
        }
        assertNotNull(restoredName)
        assertEquals("foo", restoredName?.asString())
    }

    private suspend fun createScriptFileInTempDirectory(): KtFile {
        val tmpFile = KotlinTestUtils.tmpDir("tmp").resolve("script.kts")
            .apply {
                createNewFile()
                writeText("fun foo() = 42")
            }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(tmpFile.absolutePath)!!
        val scriptFile = readAction { PsiManager.getInstance(project).findFile(virtualFile) as KtFile }
        return scriptFile
    }
}