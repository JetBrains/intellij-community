package org.jetbrains.completion.full.line.local.pipeline

import org.jetbrains.completion.full.line.local.ExecutionContext

interface CompletionPipeline<CompletionPipelineConfig, CompletionResult> {
  fun generateCompletions(
    context: String, prefix: String, config: CompletionPipelineConfig, execContext: ExecutionContext
  ): List<CompletionResult>
}