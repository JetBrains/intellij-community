// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints.compilerPlugins

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration.CompilerPluginDeclarationHighlighter
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import java.util.concurrent.Callable
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

abstract class AbstractCompilerPluginDeclarationHighlighterTest : BasePlatformTestCase(), ExpectedPluginModeProvider {
    override fun getProjectDescriptor(): LightProjectDescriptor? {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun doTest(testPath: String) {
        myFixture.module.addPluginLibraryToClassPath(KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)

        val testFile = Paths.get(testPath)
        val testFileCode = testFile.readText()

        val ktFile = myFixture.configureByText("test.kt", testFileCode) as KtFile
        val rendered = application.executeOnPooledThread(Callable {
            runReadAction {
                val lines = CompilerPluginDeclarationHighlighter.highlightCode(testFileCode, ktFile, myFixture.editor.colorsScheme)
                renderLines(lines)
            }
        }).get()

        KotlinTestUtils.assertEqualsToFile(testFile.resolveSibling(testFile.nameWithoutExtension + ".res"), rendered)
    }

    private fun renderLines(lines: List<CompilerPluginDeclarationHighlighter.CodeLine>): String = prettyPrint {
        printCollection(lines, separator = "\n") { line ->
            printCollection(line.tokens, separator = " ") { token ->
                append("[${token.text}]")
                if (token.tags.isNotEmpty()) {
                    append("{${token.tags.joinToString(", ") { it.render() }}")
                }
            }
        }
    }

    private fun CompilerPluginDeclarationHighlighter.TokenTag.render(): String = when (this) {
        is CompilerPluginDeclarationHighlighter.TokenTag.Target -> {
            val element = targetPointer.element ?: return "T(CANNOT_DEREFFERENCE_POINTER)"
            "T(${element.kotlinFqName?.asString() ?: "NO_FQ_NAME"})"
        }
    }

}