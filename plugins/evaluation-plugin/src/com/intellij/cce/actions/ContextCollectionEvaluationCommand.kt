// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.core.*
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluable.common.getEditorSafe
import com.intellij.cce.evaluable.completion.BaseCompletionActionsInvoker
import com.intellij.cce.evaluation.*
import com.intellij.cce.evaluation.step.SetupStatsCollectorStep
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterReader
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.report.MultiLineFileReportGenerator
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.util.Progress
import com.intellij.cce.util.getAs
import com.intellij.cce.util.getIfExists
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import java.lang.reflect.Type
import java.nio.file.Paths
import java.util.*

internal class ContextCollectionEvaluationCommand : CompletionEvaluationStarter.EvaluationCommand(
  name = "context",
  help = "Runs evaluation and collects completion contexts"
) {
  private val configPath by argument(
    name = "config-path",
    help = "Path to config"
  ).default(ConfigFactory.DEFAULT_CONFIG_NAME)

  override fun run() {
    val feature = EvaluableFeature.forFeature(featureName) ?: error("There is no support for the $featureName")
    val config = loadConfig(Paths.get(configPath), feature.getStrategySerializer())
    val workspace = EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
    val evaluationRootInfo = EvaluationRootInfo(true)
    feature.prepareEnvironment(config, workspace).use { environment ->
      check(environment is ProjectActionsEnvironment)

      val actions = environment.config
      val newEnvironment = object : ProjectActionsEnvironment(
        config.strategy,
        actions,
        config.interpret.filesLimit,
        config.interpret.sessionsLimit,
        evaluationRootInfo,
        environment.project,
        environment.processor,
        environment.setupSteps,
        feature.name,
        environment.featureInvoker
      ) {
        override fun prepare(datasetContext: DatasetContext, progress: Progress) {
          val files = runReadAction {
            FilesHelper.getFilesOfLanguage(project, actions.evaluationRoots, actions.ignoreFileNames, actions.language)
          }.sortedBy { it.name }
          val strategy = config.strategy as CompletionContextCollectionStrategy
          val sampled = files.shuffled(Random(strategy.samplingSeed)).take(strategy.samplesCount).sortedBy { it.name }
          generateActions(datasetContext, actions.language, sampled, evaluationRootInfo, progress)
        }
      }

      val datasetContext = DatasetContext(workspace, workspace, null)

      val stepFactory = BackgroundStepFactory(
        feature = feature,
        environment = environment,
        config = config,
        inputWorkspacePaths = null,
        datasetContext = datasetContext
      )

      val process = EvaluationProcess.build(newEnvironment, stepFactory) {
        shouldGenerateActions = true
        shouldInterpretActions = true
        shouldGenerateReports = false
        shouldReorderElements = config.reorder.useReordering
      }
      process.start(workspace)
    }
  }
}

internal enum class ContextSplitStrategy {
  TokenMiddle,
  LineMiddle,
  LineBeginning,
  BlockBeginning
}

internal data class CompletionContextCollectionStrategy(
  val suggestionsProvider: String,
  val splitStrategy: ContextSplitStrategy,
  val samplesCount: Int,
  val samplingSeed: Long,
  override val filters: Map<String, EvaluationFilter>
) : EvaluationStrategy

private class ContextCollectionActionsInvoker(
  project: Project,
  language: Language,
  private val strategy: CompletionContextCollectionStrategy
) : BaseCompletionActionsInvoker(project, language) {
  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties, sessionId: String): Session {
    val editor = runReadAction {
      getEditorSafe(project)
    }
    runInEdt {
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
    val session = Session(offset, expectedText, expectedText.length, TokenProperties.UNKNOWN, sessionId)
    val lookup = getSuggestions(expectedText, editor, strategy.suggestionsProvider)
    session.addLookup(lookup)
    return session
  }

  override fun comparator(generated: String, expected: String): Boolean {
    return !(generated.isEmpty() || !expected.startsWith(generated))
  }
}

private class ContextCollectionMultiLineProcessor(private val strategy: CompletionContextCollectionStrategy) : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    actions {
      runReadAction {
        check(code is CodeFragmentWithPsi)
        val file = code.psi.dereference() as? PsiFile ?: return@runReadAction
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        checkNotNull(document) { "There should've been a document instance for $file (${file.virtualFile})" }
        when (strategy.splitStrategy) {
          ContextSplitStrategy.LineMiddle, ContextSplitStrategy.LineBeginning -> {
            val lines = 0 until document.lineCount
            val sampled = lines.sampled(seed = strategy.samplingSeed, count = strategy.samplesCount)
            for (line in sampled) {
              val startOffset = document.getLineStartOffset(line)
              val endOffset = document.getLineEndOffset(line)
              val splitOffset = when (strategy.splitStrategy) {
                ContextSplitStrategy.LineMiddle -> startOffset + (endOffset - startOffset) / 2
                ContextSplitStrategy.LineBeginning -> startOffset
                else -> error("")
              }
              session {
                createActions(
                  document = document,
                  startOffset = startOffset,
                  endOffset = endOffset,
                  splitOffset = splitOffset
                )
              }
            }
          }
          ContextSplitStrategy.TokenMiddle -> {
            val elements = SyntaxTraverser.psiTraverser(file).toList()
            val sampled = elements.sampled(seed = strategy.samplingSeed, count = strategy.samplesCount)
            for (element in sampled) {
              val startOffset = element.startOffset
              val endOffset = element.endOffset
              val splitOffset = startOffset + (endOffset - startOffset) / 2
              session {
                createActions(
                  document = document,
                  startOffset = startOffset,
                  endOffset = endOffset,
                  splitOffset = splitOffset
                )
              }
            }
          }
          ContextSplitStrategy.BlockBeginning -> {
            val elements = code.getChildren().filterIsInstance<CodeTokenWithPsi>().mapNotNull { it.psi.dereference() }
            val sampled = elements.sampled(seed = strategy.samplingSeed, count = strategy.samplesCount)
            for (element in sampled) {
              session {
                createActions(
                  document = document,
                  startOffset = element.startOffset,
                  endOffset = element.endOffset,
                  splitOffset = element.startOffset
                )
              }
            }
          }
        }
      }
    }
  }

  private fun <Element : Comparable<Element>> Iterable<Element>.sampled(seed: Long, count: Int): List<Element> {
    return shuffled(Random(seed)).take(count).sorted()
  }

  @JvmName("sampledElements")
  private fun Iterable<PsiElement>.sampled(seed: Long, count: Int): List<PsiElement> {
    return shuffled(Random(seed)).take(count).sortedBy { it.startOffset }
  }

  private fun ActionsBuilder.SessionBuilder.createActions(document: Document, startOffset: Int, endOffset: Int, splitOffset: Int) {
    val textAfterPrefix = document.text.substring(splitOffset)
    val localMiddleEndOffset = findLessIndent(textAfterPrefix, index = 10)
    val middleText = textAfterPrefix.substring(0, localMiddleEndOffset)
    val middleEndOffset = localMiddleEndOffset + splitOffset
    addDefaultActions(splitOffset, middleEndOffset, middleText)
  }

  private fun findLessIndent(text: String, index: Int): Int {
    val lines = text.split("\n")
    val first_line_indent = lines[0].length - lines[0].trimStart().length
    var position = 0
    for ((i, line) in lines.withIndex()) {
      val line_indent = line.length - line.trimStart().length
      if (line_indent < first_line_indent) {
        return position
      }
      else {
        position += line.length + 1
      }
      if (i == index) {
        return position
      }
    }
    return text.length
  }

  private fun ActionsBuilder.SessionBuilder.addDefaultActions(offset: Int, endOffset: Int, expectedText: String) {
    moveCaret(offset)
    deleteRange(offset, endOffset)
    callFeature(expectedText, offset, TokenProperties.UNKNOWN)
    printText(expectedText)
  }
}

private class ContextCollectionStrategySerializer : StrategySerializer<CompletionContextCollectionStrategy> {
  override fun serialize(source: CompletionContextCollectionStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject {
    return JsonObject().apply {
      addProperty("suggestionsProvider", source.suggestionsProvider)
      addProperty("splitStrategy", source.splitStrategy.name)
      addProperty("samplesCount", source.samplesCount)
      addProperty("samplingSeed", source.samplingSeed)
      val filters = JsonObject()
      source.filters.forEach { (id, filter) -> filters.add(id, filter.toJson()) }
      add("filters", filters)
    }
  }

  override fun deserialize(map: Map<String, Any>, language: String): CompletionContextCollectionStrategy {
    return CompletionContextCollectionStrategy(
      suggestionsProvider = map.getAs("suggestionsProvider"),
      splitStrategy = ContextSplitStrategy.valueOf(map.getAs("splitStrategy")),
      samplesCount = map.getAs<Double>("samplesCount").toInt(),
      samplingSeed = map.getAs<Double>("samplingSeed").toLong(),
      filters = EvaluationFilterReader.readFilters(map.getIfExists("filters"), language)
    )
  }
}

internal class ContextCollectionFeature : EvaluableFeatureBase<CompletionContextCollectionStrategy>("completion-context") {
  override fun getGenerateActionsProcessor(strategy: CompletionContextCollectionStrategy, project: Project): GenerateActionsProcessor {
    return ContextCollectionMultiLineProcessor(strategy)
  }

  override fun getFeatureInvoker(project: Project, language: Language, strategy: CompletionContextCollectionStrategy): FeatureInvoker {
    return ContextCollectionActionsInvoker(project, language, strategy)
  }

  override fun getStrategySerializer(): StrategySerializer<CompletionContextCollectionStrategy> {
    return ContextCollectionStrategySerializer()
  }

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    featuresStorages: List<FeaturesStorage>,
    fullLineStorages: List<FullLineLogsStorage>,
    dirs: GeneratorDirectories
  ): MultiLineFileReportGenerator {
    return MultiLineFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs)
  }

  override fun getMetrics(): List<Metric> {
    return emptyList()
  }

  override fun getEvaluationSteps(language: Language, strategy: CompletionContextCollectionStrategy): List<EvaluationStep> {
    return emptyList()
  }
}
