// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch.output

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchFile

interface ScratchOutputHandler {
    fun onStart(file: KotlinScratchFile)
    fun handle(file: KotlinScratchFile, output: ScratchOutput)
    fun handle(file: KotlinScratchFile, explanations: List<ExplainInfo>, scope: CoroutineScope)
    fun error(file: KotlinScratchFile, message: String)
    fun onFinish(file: KotlinScratchFile)
    fun clear(file: KotlinScratchFile)
}

data class ScratchOutput(val text: String, val type: ScratchOutputType)

enum class ScratchOutputType {
    RESULT,
    OUTPUT,
    ERROR
}

class ExplainInfo(
    val variableName: String, val offsets: Pair<Int, Int>, val variableValue: Any?, val line: Int?
) {
    override fun toString(): String {
        return "ExplainInfo(variableName='$variableName', offsets=$offsets, variableValue=$variableValue, line=$line)"
    }
}

open class ScratchOutputHandlerAdapter : ScratchOutputHandler {
    override fun onStart(file: KotlinScratchFile) {}
    override fun handle(file: KotlinScratchFile, explanations: List<ExplainInfo>, scope: CoroutineScope) {}
    override fun handle(file: KotlinScratchFile, output: ScratchOutput) {}
    override fun error(file: KotlinScratchFile, message: String) {}
    override fun onFinish(file: KotlinScratchFile) {}
    override fun clear(file: KotlinScratchFile) {}
}