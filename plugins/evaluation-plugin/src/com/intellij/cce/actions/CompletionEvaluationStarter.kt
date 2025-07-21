// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.intellij.cce.commands.evaluationCommandExtensions
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.LAYOUT_NAME
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.FinishEvaluationStep
import com.intellij.cce.evaluation.step.SetupStatsCollectorStep
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.util.ExceptionsUtil.stackTraceToString
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.commandNameFromExtension
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ex.ApplicationEx.FORCE_EXIT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.currentClassLogger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

internal class CompletionEvaluationStarter : ApplicationStarter {
  override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  override fun main(args: List<String>) {

    fun run() = MainEvaluationCommand()
      .subcommands(
        FullCommand(),
        GenerateActionsCommand(),
        CustomCommand(),
        MultipleEvaluations(),
        CompareEvaluationsInDirectory(),
        MergeEvaluations(),
        ContextCollectionEvaluationCommand()
      )
      .also { command ->
        evaluationCommandExtensions.forEach {
          it.extend(command)
        }
      }
      .main(args.toList().subList(1, args.size))


    val startTimestamp = System.currentTimeMillis()
    try {
      run()
      val delta = 5_000 - (System.currentTimeMillis() - startTimestamp)
      if (delta > 0) {
        Thread.sleep(delta) // for graceful shutdown
      }
      exit(0)
    }
    catch (e: FinishEvaluationStep.EvaluationCompletedWithErrorsException) {
      fatalError(e.message!!)
    }
    catch (e: Exception) {
      fatalError("Evaluation failed $e. StackTrace: ${stackTraceToString(e)}")
    }
  }

  abstract class EvaluationCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

    protected val featureName by argument(name = "Feature name").default("rename")

    protected fun <T : EvaluationStrategy> loadConfig(configPath: Path, strategySerializer: StrategySerializer<T>): Config {
      try {
        println("Load config: $configPath")
        val config = ConfigFactory.load(configPath, strategySerializer)
        println("Config loaded!")
        return config
      }
      catch (e: Throwable) {
        fatalError("Error for loading config: $configPath, $e. StackTrace: ${stackTraceToString(e)}")
        throw e
      }
    }

    protected fun runPreliminarySteps(feature: EvaluableFeature<*>, workspace: EvaluationWorkspace) {
      for (step in feature.getPreliminaryEvaluationSteps()) {
        println("Starting preliminary step: ${step.name}")
        step.runInIntellij(null, workspace)
      }
    }
  }

  inner class MainEvaluationCommand : EvaluationCommand(commandNameFromExtension!!, "Evaluate code completion quality in headless mode") {
    override fun run() = Unit
  }

  abstract class EvaluationCommandBase(name: String, help: String) : EvaluationCommand(name, help) {
    private val configPath by argument(name = "config-path", help = "Path to config").default(ConfigFactory.DEFAULT_CONFIG_NAME)

    override fun run() {
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the $featureName")
      val config = loadConfig(Paths.get(configPath), feature.getStrategySerializer())
      val workspace = EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
      val datasetContext = DatasetContext(workspace, workspace, configPath)
      runPreliminarySteps(feature, workspace)
      feature.prepareEnvironment(config, workspace).use { environment ->
        val stepFactory = BackgroundStepFactory(feature, config, environment, null, datasetContext)
        EvaluationProcess.build(environment, stepFactory) {
          customize()
          shouldReorderElements = config.reorder.useReordering
        }.start(workspace)
      }
    }

    protected abstract fun EvaluationProcess.Builder.customize()
  }

  class GenerateActionsCommand
    : EvaluationCommandBase("actions", "Generate actions without interpreting") {
    override fun EvaluationProcess.Builder.customize() {
      shouldGenerateActions = true
    }
  }

  class FullCommand
    : EvaluationCommandBase("full", "Start process from actions generation (set up by config)") {
    override fun EvaluationProcess.Builder.customize() {
      shouldGenerateActions = true
      shouldInterpretActions = true
      shouldGenerateReports = true
    }
  }

  class CustomCommand : EvaluationCommand(name = "custom", help = "Start process from actions interpretation or report generation") {

    private val workspacePath by argument(name = "workspace", help = "Path to workspace")
    private val interpretActions by option(names = arrayOf("--interpret-actions", "-i"), help = "Interpret actions").flag()
    private val generateReport by option(names = arrayOf("--generate-report", "-r"), help = "Generate report").flag()
    private val reorderElements by option(names = arrayOf("--reorder-elements", "-e"), help = "Reorder elements").flag()

    override fun run() {
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the feature")
      val workspace = EvaluationWorkspace.open(workspacePath, SetupStatsCollectorStep.statsCollectorLogsDirectory)
      val datasetContext = DatasetContext(workspace, workspace, null)
      val config = workspace.readConfig(feature.getStrategySerializer())
      runPreliminarySteps(feature, workspace)
      feature.prepareEnvironment(config, workspace).use { environment ->
        val stepFactory = BackgroundStepFactory(feature, config, environment, null, datasetContext)
        val process = EvaluationProcess.build(environment, stepFactory) {
          shouldGenerateActions = false
          shouldInterpretActions = interpretActions
          shouldReorderElements = reorderElements
          shouldGenerateReports = generateReport
        }
        process.start(workspace)
      }
    }
  }

  abstract class MultipleEvaluationsBase(name: String, help: String) : EvaluationCommand(name, help) {

    abstract fun getWorkspaces(): List<String>

    override fun run() {
      val workspacesToCompare = getWorkspaces()
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the feature")
      val config = workspacesToCompare.map { EvaluationWorkspace.open(it, SetupStatsCollectorStep.statsCollectorLogsDirectory) }.buildMultipleEvaluationsConfig(
        feature.getStrategySerializer(),
        "COMPARING",
      )
      val outputWorkspace = EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
      val datasetContext = DatasetContext(outputWorkspace, null, null)
      feature.prepareEnvironment(config, outputWorkspace).use { environment ->
        val stepFactory = BackgroundStepFactory(feature, config, environment, workspacesToCompare, datasetContext)
        val process = EvaluationProcess.build(environment, stepFactory) {
          shouldGenerateReports = true
        }
        process.start(outputWorkspace)
      }
    }
  }

  class MultipleEvaluations : MultipleEvaluationsBase(name = "multiple-evaluations",
                                                      help = "Generate comparing report by multiple evaluations") {
    private val workspacesArg by argument(name = "workspaces", help = "List of workspaces").multiple()

    override fun getWorkspaces(): List<String> = workspacesArg
  }

  class CompareEvaluationsInDirectory : MultipleEvaluationsBase(name = "compare-in",
                                                                help = "Generate comparing report for all evaluation workspaces in a directory") {
    private val root by argument(name = "directory", help = "Root directory for evaluation workspaces")

    override fun getWorkspaces(): List<String> = readWorkspacesFromDirectory(root)
  }

  class MergeEvaluations : EvaluationCommand(name = "merge-from",
                                             help = "Generate merged report for all evaluation workspaces in a directory") {
    private val root by argument(name = "directory", help = "Root directory for evaluation workspaces")

    override fun run() {
      val workspacesToMerge = readWorkspacesFromDirectory(root)
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the feature")
      val config = workspacesToMerge.map { EvaluationWorkspace.open(it, SetupStatsCollectorStep.statsCollectorLogsDirectory) }.buildMultipleEvaluationsConfig(
        feature.getStrategySerializer()
      )
      val outputWorkspace = EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
      val datasetContext = DatasetContext(outputWorkspace, null, null)
      for (workspacePath in workspacesToMerge) {
        val workspace = EvaluationWorkspace.open(workspacePath, SetupStatsCollectorStep.statsCollectorLogsDirectory)
        workspace.readAdditionalStats(LAYOUT_NAME)?.let {
          outputWorkspace.saveAdditionalStats(LAYOUT_NAME, it)
        }
        val sessionFiles = workspace.getSessionFilesSafe()
        for (sessionFile in sessionFiles) {
          outputWorkspace.sessionsStorage.saveSessions(workspace.sessionsStorage.getSessions(sessionFile.first))
        }
      }
      outputWorkspace.saveMetadata()
      feature.prepareEnvironment(config, outputWorkspace).use { environment ->
        val stepFactory = BackgroundStepFactory(feature, config, environment, null, datasetContext)
        val process = EvaluationProcess.build(environment, stepFactory) {
          shouldGenerateReports = true
        }
        process.start(outputWorkspace)
      }
    }

    /**
     * Let's not fail the aggregation of the evaluation report if some
     * child builds failed (and so some files are missing).
     */
    private fun EvaluationWorkspace.getSessionFilesSafe(): List<Pair<String, String>> =
      try {
        sessionsStorage.getSessionFiles()
      }
      catch (e: Throwable) {
        logger.warn("Failed to get session files from workspace ${this.path()}. Probably some evaluation builds failed")
        emptyList()
      }
  }

  companion object {
    val logger = currentClassLogger()
  }
}

private fun readWorkspacesFromDirectory(directory: String): List<String> {
  val outputDirectory = Paths.get(directory)
  if (!outputDirectory.exists() || !outputDirectory.isDirectory()) {
    throw BadParameterValue("Directory \"$directory\" not found.")
  }

  val nestedFiles = outputDirectory.toFile().listFiles() ?: emptyArray()

  val result = nestedFiles.filter { it.isDirectory }.map { it.absolutePath }
  if (result.isEmpty()) {
    throw BadParameterValue("Directory \"$directory\" should not be empty")
  }

  return result
}

private fun exit(exitCode: Int) {
  try {
    ApplicationManagerEx.getApplicationEx().exit(FORCE_EXIT, exitCode)
    throw IllegalStateException("Process should be finished!!!")
  }
  catch (_: Throwable) {
    exitProcess(exitCode)
  }
}

private fun fatalError(msg: String) {
  System.err.println("Evaluation failed: $msg")
  exit(1)
}