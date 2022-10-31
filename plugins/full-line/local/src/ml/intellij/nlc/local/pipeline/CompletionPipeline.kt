package ml.intellij.nlc.local.pipeline

import io.kinference.model.ExecutionContext

interface CompletionPipeline<CompletionPipelineConfig, CompletionResult> {
  fun generateCompletions(
    context: String, prefix: String, config: CompletionPipelineConfig, execContext: ExecutionContext
  ): List<CompletionResult>
}