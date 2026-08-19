package com.intellij.workspaceModel.codegen.impl.dsl

import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.GeneratorSettings

// BUILDERS
fun GeneratorContext.generateCode(block: CodeContext.() -> Unit): String {
  if (hasErrors()) return ""
  val codeContext = CodeContextImpl(this)
  codeContext.block()
  return codeContext.result
}

fun <R> generatorContext(generatorSettings: GeneratorSettings, block: GeneratorContext.() -> R): R {
  val context = GeneratorContextImpl(generatorSettings.explicitApiEnabled, generatorSettings.testModeEnabled)
  val result = context.block()
  return result
}

// CONTEXT IMPL CLASSES
private class CodeContextImpl(override val parentContext: GeneratorContext) : CodeContext, GeneratorContext by parentContext {
  val stringBuilder: StringBuilder = StringBuilder()

  override val result: String
    get() = stringBuilder.toString()

  override fun line(string: String) {
    stringBuilder.appendLine(string)
  }

  override fun lineNoNl(string: String) {
    stringBuilder.append(string)
  }

  override fun section(head: String, body: CodeContext.() -> Unit) {
    stringBuilder.append(head)
    stringBuilder.appendLine("{")
    body()
    stringBuilder.appendLine("}")
  }

  override fun sectionNoBrackets(head: String, block: CodeContext.() -> Unit) {
    stringBuilder.appendLine(head)
    block()
  }
}

private class GeneratorContextImpl(
  private val explicitApiEnabled: Boolean,
  override val testModeEnabled: Boolean,
) : GeneratorContext {
  private val mutableProblems: MutableList<GenerationProblem> = ArrayList()

  override val explicitApiModifier: String
    get() = if (explicitApiEnabled) "public " else ""

  override val problems: List<GenerationProblem>
    get() = mutableProblems

  override fun reportProblem(problem: GenerationProblem) {
    mutableProblems.add(problem)
  }
}
