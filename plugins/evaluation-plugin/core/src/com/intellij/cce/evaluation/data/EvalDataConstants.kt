package com.intellij.cce.evaluation.data

import com.intellij.cce.evaluable.*
import com.intellij.cce.metric.*
import com.intellij.cce.metric.ExternalApiRecall.Companion.AIA_GROUND_TRUTH_EXTERNAL_API_CALLS
import com.intellij.cce.metric.ExternalApiRecall.Companion.AIA_PREDICTED_EXTERNAL_API_CALLS
import com.intellij.cce.metric.context.MeanContextLines
import com.intellij.cce.metric.context.MeanContextSize

object Result {
  val CURRENT_FILE_UPDATE: EvalDataDescription<String, TextUpdate> = EvalDataDescription(
    name = "Current file update",
    description = "Bind with the result content of the current file",
    DataPlacement.CurrentFileUpdate,
    presentation = EvalDataPresentation(
      PresentationCategory.RESULT,
      DataRenderer.TextDiff,
      DynamicName.CurrentFileName,
    )
  )

  val FILE_UPDATES: EvalDataDescription<List<FileUpdate>, FileUpdate> = EvalDataDescription(
    name = "File updates",
    description = "Bind with all updated files",
    DataPlacement.FileUpdates("file_updates"),
    presentation = EvalDataPresentation(
      PresentationCategory.RESULT,
      DataRenderer.TextDiff,
      DynamicName.FileName,
      ignoreMissingData = true
    )
  )

  val EXPECTED_FILE_UPDATES: EvalDataDescription<List<FileUpdate>, FileUpdate> = EvalDataDescription(
    name = "Expected file updates",
    description = "Bind with all expected updated files",
    DataPlacement.FileUpdates("expected_file_updates"),
    presentation = EvalDataPresentation(
      PresentationCategory.RESULT,
      DataRenderer.TextDiff,
      DynamicName.Formatted("Expected ", DynamicName.FileName),
      ignoreMissingData = true
    )
  )

  val COLORED_INSIGHTS: EvalDataDescription<List<ColoredInsightsData>, ColoredInsightsData> = EvalDataDescription(
    name = "Colored insights",
    description = "Bind with colored insights (model, positive, negative)",
    DataPlacement.ColoredInsightsPlacement("colored_insights"),
    presentation = EvalDataPresentation(
      PresentationCategory.RESULT,
      DataRenderer.ColoredInsights,
      DynamicName.ColoredInsightsFileName,
      ignoreMissingData = true
    )
  )
}

object Execution {
  val LATENCY: TrivialEvalData<Long> = EvalDataDescription(
    name = "Latency",
    description = "Bind with millis spent for inference",
    DataPlacement.Latency,
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.InlineLong
    )
  )

  val USER_REQUEST: TrivialEvalData<String> = EvalDataDescription(
    name = "User request",
    description = "Request provided by user",
    DataPlacement.AdditionalText(AIA_USER_PROMPT),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text(wrapping = true),
    )
  )

  val LLM_RESPONSE: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM response",
    description = "LLM response",
    DataPlacement.AdditionalText(AIA_RESPONSE),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text()
    )
  )

  val LLM_JUDGE_RESPONSE: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM judge response",
    description = "Raw response of the llm as a judge",
    placement = DataPlacement.AdditionalText(AIA_LLM_JUDGE_RESPONSE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text()
    )
  )

  val LLM_JUDGE_SCORE: TrivialEvalData<Double> = EvalDataDescription(
    name = "LLM judge score",
    description = "The LLM Judge score, parsed from the raw LLM judge response",
    placement = DataPlacement.AdditionalDouble(AIA_LLM_JUDGE_SCORE_KEY),
    presentation = EvalDataPresentation(
      PresentationCategory.METRIC,
      DataRenderer.InlineDouble
    )
  )

  val LLM_CONTEXT: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM context",
    description = "Result prompt used for LLM",
    DataPlacement.AdditionalText(AIA_CONTEXT),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text()
    )
  )

  val LLM_SYSTEM_CONTEXT: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM system context",
    description = "Result system prompt used for LLM",
    DataPlacement.AdditionalText(AIA_SYSTEM_CONTEXT),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text()
    )
  )

  val EXTRACTED_SNIPPETS_FROM_LLM_RESPONSE: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Code snippets from LLM response",
    description = "Bind with code snippets extracted LLM response",
    DataPlacement.AdditionalJsonSerializedStrings(AIA_EXTRACTED_CODE_SNIPPETS),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Snippets
    )
  )

  val LLM_CHAT_DUMP: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM chat dump",
    description = "Full dump of the chat session including system context, messages, and metadata",
    placement = DataPlacement.AdditionalText(AIA_CHAT_DUMP),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text()
    )
  )

  val REFERENCE: TrivialEvalData<String> = EvalDataDescription(
    name = "Reference",
    description = null,
    DataPlacement.AdditionalText(REFERENCE_PROPERTY),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text(wrapping = true),
    )
  )

  val NAME: TrivialEvalData<String> = EvalDataDescription(
    name = "Name",
    description = "Some description of an evaluation case",
    DataPlacement.AdditionalText(AIA_NAME),
  )

  val DESCRIPTION: TrivialEvalData<String> = EvalDataDescription(
    name = "Preview",
    description = "Some description of an evaluation case",
    DataPlacement.AdditionalText(AIA_DESCRIPTION),
  )

  val LLMC_LOG: TrivialEvalData<String> = EvalDataDescription(
    name = "LLMC log",
    description = "LLMC logs during evaluation case",
    placement = DataPlacement.AdditionalText(AIA_LLMC_LOG),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text(wrapping = true),
      ignoreMissingData = true,
    )
  )

  val HTTP_LOG: TrivialEvalData<String> = EvalDataDescription(
    name = "HTTP log",
    description = "HTTP logs during evaluation case",
    placement = DataPlacement.AdditionalText(AIA_HTTP_LOG),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text(wrapping = true),
      ignoreMissingData = true,
    )
  )

  val REFERENCE_NAMED_RANGES: TrivialEvalData<List<NamedRange>> = EvalDataDescription(
    name = "Reference named ranges",
    description = "Ground truth named ranges",
    DataPlacement.AdditionalNamedRanges(REFERENCE_NAMED_RANGE_PROPERTY),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.NamedRanges
    )
  )

  val PREDICTED_NAMED_RANGES: TrivialEvalData<List<NamedRange>> = EvalDataDescription(
    name = "Predicted named ranges",
    description = "Predicted named ranges",
    DataPlacement.AdditionalNamedRanges(PREDICTED_NAMED_RANGE_PROPERTY),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.NamedRanges
    )
  )
}

object Analysis {
  val HAS_SYNTAX_ERRORS: TrivialEvalData<Boolean> = EvalDataDescription(
    name = "Has syntax errors",
    description = "Bind with `true` if the result has syntax errors",
    DataPlacement.AdditionalBoolean(AIA_HAS_SYNTAX_ERRORS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineBoolean,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.WITHOUT_SYNTAX_ERRORS }
    )
  )

  val CODE_IS_COMPILABLE: TrivialEvalData<Boolean> = EvalDataDescription(
    name = "Code Is Compilable",
    description = "Generated code is compiling successfully",
    DataPlacement.AdditionalBoolean(AIA_CODE_IS_COMPILABLE),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineBoolean,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromValue { !it }
    )
  )

  val HIGHLIGHT_ERRORS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Highlight errors and warnings",
    description = "Bind with the list of appeared highlights in format `[ERROR] error_description` or `[WARNING] warning_description]`",
    DataPlacement.AdditionalConcatenatedLines(AIA_HIGHLIGHT_ERRORS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.WITHOUT_HIGHLIGHT_ERRORS }
    )
  )

  val EXECUTION_EXIT_CODE: TrivialEvalData<Int> = EvalDataDescription(
    name = "Execution exit code",
    description = "Bind with the exit code of the execution-based tests",
    DataPlacement.AdditionalInt("performance_exit_code"),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.InlineInt,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromValue { it != 0 }
    )
  )

  val ERASED_APIS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Erased APIs",
    description = "Bind with the list of erased API names",
    DataPlacement.AdditionalConcatenatedLines(AIA_ERASED_APIS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.Lines,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.PRESERVED_API }
    )
  )

  val CONTEXT_COLLECTION_DURATION: TrivialEvalData<Double> = EvalDataDescription(
    name = "Context collection duration",
    description = "Bind with the sum of durations of all context collection components",
    DataPlacement.AdditionalDouble(AIA_CONTEXT_COLLECTION_DURATION_MS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineDouble,
    ),
  )

  val GROUND_TRUTH_API_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Ground truth internal API calls",
    description = "Bind with the list of initial internal API calls",
    DataPlacement.AdditionalConcatenatedLines(AIA_GROUND_TRUTH_INTERNAL_API_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
  )

  val GROUND_TRUTH_EXTERNAL_API_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Ground truth external API calls",
    description = "Bind with the list of initial external API calls",
    DataPlacement.AdditionalConcatenatedLines(AIA_GROUND_TRUTH_EXTERNAL_API_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    )
  )

  val PREDICTED_API_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Predicted internal API calls",
    description = "Bind with the list of predicted internal API calls",
    DataPlacement.AdditionalConcatenatedLines(AIA_PREDICTED_API_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
  )

  val PREDICTED_EXTERNAL_API_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Predicted external API calls",
    description = "Bind with the list of predicted external API calls",
    DataPlacement.AdditionalConcatenatedLines(AIA_PREDICTED_EXTERNAL_API_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    )
  )

  val FAILED_FILE_VALIDATIONS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Failed file validations",
    description = "Bind with failed file validations",
    placement = DataPlacement.AdditionalConcatenatedLines(AIA_FAILED_FILE_VALIDATIONS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.Lines,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.FILE_VALIDATIONS_SUCCESS }
    )
  )

  val FAILED_RELATED_FILE_VALIDATIONS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Failed related file validations",
    description = "Bind with failed file validations in related files",
    placement = DataPlacement.AdditionalConcatenatedLines(AIA_FAILED_RELATED_FILE_VALIDATIONS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.Lines,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.FILE_VALIDATIONS_SUCCESS }
    )
  )

  val EXPECTED_FUNCTION_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Expected function calls",
    description = "Bind with the list of expected internal API calls",
    placement = DataPlacement.AdditionalConcatenatedLines(AIA_EXPECTED_FUNCTION_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.FUNCTION_CALLING }
    )
  )

  val ACTUAL_FUNCTION_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Actual function calls",
    description = "Bind with the list of actual internal API calls",
    placement = DataPlacement.AdditionalConcatenatedLines(AIA_ACTUAL_FUNCTION_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
  )

  val HAS_NO_EFFECT: TrivialEvalData<Boolean> = EvalDataDescription(
    name = "Has no effect",
    description = "Bind with `true` if nothing has happened",
    DataPlacement.AdditionalBoolean(AIA_HAS_NO_EFFECT),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineBoolean,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromValue { it }
    )
  )

  val EXACT_MATCH: TrivialEvalData<Double> = EvalDataDescription(
    name = "Exact match",
    description = "Bind with `true` if result matches expected one",
    DataPlacement.AdditionalDouble(AIA_EXACT_MATCH),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineDouble,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.EXACT_MATCH }
    )
  )

  val AST_MATCH: TrivialEvalData<Double> = EvalDataDescription(
    name = "Ast match",
    description = "Bind with `true` if result AST matches expected one",
    DataPlacement.AdditionalDouble(AIA_AST_MATCH),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      DataRenderer.InlineDouble,
    ),
    problemIndicators = listOf(
      ProblemIndicator.FromMetric { Metrics.AST_MATCH }
    )
  )
}

object Metrics {
  val SESSION_COUNT: EvalMetric = EvalMetric(
    showInCard = false
  ) { SessionsCountMetric() }

  val PRECISION: EvalMetric = EvalMetric(
    threshold = 1.0
  ) { PrecisionMetric() }

  val MEAN_LATENCY: EvalMetric = EvalMetric(
    showInCard = false,
    dependencies = MetricDependencies(Execution.LATENCY)
  ) { MeanLatencyMetric() }

  val MEAN_CONTEXT_SIZE: EvalMetric = EvalMetric(
    dependencies = MetricDependencies(Execution.LLM_CONTEXT)
  ) { MeanContextSize() }

  val MEAN_CONTEXT_LINES: EvalMetric = EvalMetric(
    dependencies = MetricDependencies(Execution.LLM_CONTEXT)
  ) { MeanContextLines() }

  val WITHOUT_SYNTAX_ERRORS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.HAS_SYNTAX_ERRORS)
  ) { WithoutSyntaxErrorsSessionRatio() }

  val WITHOUT_HIGHLIGHT_ERRORS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.HIGHLIGHT_ERRORS)
  ) { WithoutHighlightErrorsSessionRatio() }

  val PRESERVED_API: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.ERASED_APIS)
  ) { PreservedApi() }

  val INTERNAL_API_RECALL: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(
      Analysis.GROUND_TRUTH_API_CALLS,
      Analysis.PREDICTED_API_CALLS,
      DataRenderer.TextDiff
    ) { initial, result -> TextUpdate(initial.sorted().joinToString("\n"), result.sorted().joinToString("\n")) }
  ) { InternalApiRecall() }

  val EXTERNAL_API_RECALL: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(
      Analysis.GROUND_TRUTH_EXTERNAL_API_CALLS,
      Analysis.PREDICTED_EXTERNAL_API_CALLS,
      DataRenderer.TextDiff
    ) { initial, result -> TextUpdate(initial.sorted().joinToString("\n"), result.sorted().joinToString("\n")) }
  ) { ExternalApiRecall() }

  val FILE_VALIDATIONS_SUCCESS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.FAILED_FILE_VALIDATIONS)
  ) { FileValidationSuccess() }

  val RELATED_FILE_VALIDATIONS_SUCCESS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.FAILED_RELATED_FILE_VALIDATIONS)
  ) { RelatedFileValidationSuccess() }

  val FUNCTION_CALLING: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(
      Analysis.EXPECTED_FUNCTION_CALLS,
      Analysis.ACTUAL_FUNCTION_CALLS,
      DataRenderer.TextDiff
    ) { expected, actual -> TextUpdate(expected.sorted().joinToString("\n"), actual.sorted().joinToString("\n")) }
  ) { FunctionCallingMetric() }

  val EXACT_MATCH: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.EXACT_MATCH)
  ) { ExactMatchMetric() }

  val AST_MATCH: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.AST_MATCH)
  ) { AstMatchMetric() }
}