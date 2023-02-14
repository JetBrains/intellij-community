// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal.benchmark

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridLayoutManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.actions.internal.benchmark.AbstractCompletionBenchmarkAction.Companion.randomElement
import org.jetbrains.kotlin.idea.completion.CompletionBenchmarkSink
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import java.util.*
import kotlin.properties.Delegates


class LocalCompletionBenchmarkAction : AbstractCompletionBenchmarkAction() {

    override fun createBenchmarkScenario(
        project: Project,
        benchmarkSink: CompletionBenchmarkSink.Impl
    ): AbstractCompletionBenchmarkScenario? {

        val settings = showSettingsDialog() ?: return null

        val random = Random(settings.seed)

        fun collectFiles(): List<KtFile>? {
            val ktFiles = collectSuitableKotlinFiles(project) {
                it.getLineCount() >= settings.lines
            }

            if (ktFiles.size < settings.files) {
                showPopup(project, KotlinBundle.message("number.of.attempts.then.files.in.project.0", ktFiles.size))
                return null
            }

            val result = mutableListOf<KtFile>()
            repeat(settings.files) {
                result += ktFiles.randomElement(random)!!.also { ktFiles.remove(it) }
            }
            return result
        }

        val ktFiles = collectFiles() ?: return null

        return LocalCompletionBenchmarkScenario(ktFiles, settings, project, benchmarkSink, random)
    }


    data class Settings(val seed: Long, val lines: Int, val files: Int, val functions: Int)

    private fun showSettingsDialog(): Settings? {
        var cSeed: JBTextField by Delegates.notNull()
        var cLines: JBTextField by Delegates.notNull()
        var cFiles: JBTextField by Delegates.notNull()
        var cFunctions: JBTextField by Delegates.notNull()
        val dialogBuilder = DialogBuilder()


        val jPanel = JBPanel<JBPanel<*>>(GridLayoutManager(4, 2)).apply {
            var i = 0

            cSeed = addBoxWithLabel(KotlinBundle.message("random.seed"), default = "0", i = i++)
            cFiles = addBoxWithLabel(KotlinBundle.message("files.to.visit"), default = "20", i = i++)
            cFunctions = addBoxWithLabel(KotlinBundle.message("max.functions.to.visit"), default = "20", i = i++)
            cLines = addBoxWithLabel(KotlinBundle.message("file.lines"), default = "100", i = i)
        }
        dialogBuilder.centerPanel(jPanel)
        if (!dialogBuilder.showAndGet()) return null

        return Settings(
            cSeed.text.toLong(),
            cLines.text.toInt(),
            cFiles.text.toInt(),
            cFunctions.text.toInt()
        )
    }

}

internal class LocalCompletionBenchmarkScenario(
    val files: List<KtFile>,
    val settings: LocalCompletionBenchmarkAction.Settings,
    project: Project, benchmarkSink: CompletionBenchmarkSink.Impl,
    random: Random
) : AbstractCompletionBenchmarkScenario(project, benchmarkSink, random) {
    override suspend fun doBenchmark() {
        val allResults = mutableListOf<Result>()
        files.forEach { file ->
            val functions = file.collectDescendantsOfType<KtFunction> { it.hasBlockBody() && it.bodyExpression != null }.toMutableList()
            val randomFunctions = generateSequence { functions.randomElement(random).also { functions.remove(it) } }
            randomFunctions.take(settings.functions).forEach { function ->
                val offset = function.bodyExpression!!.endOffset - 1
                allResults += typeAtOffsetAndGetResult("listOf(1).", offset, file)
            }
        }
        saveResults(allResults)
    }
}