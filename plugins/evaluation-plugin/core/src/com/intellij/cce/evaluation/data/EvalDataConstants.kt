package com.intellij.cce.evaluation.data

import com.intellij.cce.evaluable.*
import com.intellij.cce.metric.*
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
      DataRenderer.Text
    )
  )

  val LLM_RESPONSE: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM response",
    description = "LLM response",
    DataPlacement.AdditionalText(AIA_RESPONSE),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text
    )
  )

  val LLM_CONTEXT: TrivialEvalData<String> = EvalDataDescription(
    name = "LLM context",
    description = "Result prompt used for LLM",
    DataPlacement.AdditionalText(AIA_CONTEXT),
    presentation = EvalDataPresentation(
      PresentationCategory.EXECUTION,
      DataRenderer.Text
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

  val GROUND_TRUTH_API_CALLS: TrivialEvalData<List<String>> = EvalDataDescription(
    name = "Ground truth internal API calls",
    description = "Bind with the list of initial internal API calls",
    DataPlacement.AdditionalConcatenatedLines(AIA_GROUND_TRUTH_INTERNAL_API_CALLS),
    presentation = EvalDataPresentation(
      PresentationCategory.ANALYSIS,
      renderer = DataRenderer.Lines,
    ),
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

  val API_RECALL: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(
      Analysis.GROUND_TRUTH_API_CALLS,
      Analysis.PREDICTED_API_CALLS,
      DataRenderer.TextDiff
    ) { initial, result -> TextUpdate(initial.sorted().joinToString("\n"), result.sorted().joinToString("\n")) }
  ) { ApiRecall() }

  val FILE_VALIDATIONS_SUCCESS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.FAILED_FILE_VALIDATIONS)
  ) { FileValidationSuccess() }

  val RELATED_FILE_VALIDATIONS_SUCCESS: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.FAILED_RELATED_FILE_VALIDATIONS)
  ) { RelatedFileValidationSuccess() }

  val EXACT_MATCH: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.EXACT_MATCH)
  ) { ExactMatchMetric() }

  val AST_MATCH: EvalMetric = EvalMetric(
    threshold = 1.0,
    dependencies = MetricDependencies(Analysis.AST_MATCH)
  ) { AstMatchMetric() }
}