// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.util.ExceptionsUtil.stackTraceToString
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

internal class CompletionEvaluationStarter : ApplicationStarter {
  override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  override val commandName: String
    get() = "ml-evaluate"

  override fun main(args: List<String>) {
    MainEvaluationCommand()
      .subcommands(FullCommand(), GenerateActionsCommand(), CustomCommand(),
                   MultipleEvaluations(), CompareEvaluationsInDirectory(), MergeEvaluations())
      .main(args.toList().subList(1, args.size))
  }

  abstract class EvaluationCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

    protected val featureName by argument(name = "Feature name").default("rename")

    protected fun <T : EvaluationStrategy> loadConfig(configPath: Path, strategySerializer: StrategySerializer<T>) = try {
      println("Load config: $configPath")
      val config = ConfigFactory.load(configPath, strategySerializer)
      println("Config loaded!")
      config
    }
    catch (e: Throwable) {
      fatalError("Error for loading config: $configPath, $e. StackTrace: ${stackTraceToString(e)}")
    }

    protected fun loadAndApply(projectPath: String, action: (Project) -> Unit) {
      val parentDisposable = Disposer.newDisposable()
      val project: Project?

      try {
        println("Open and load project $projectPath. Operation may take a few minutes.")
        project = runBlockingCancellable {
          ProjectOpeningUtils.openProject(
            File(projectPath).toPath(),
            parentDisposable,
          )
        }
        println("Project loaded!")

        try {
          action(project)
        }
        catch (exception: Exception) {
          throw RuntimeException("Failed to run actions on the project: $exception")
        }
      }
      catch (exception: Exception) {
        fatalError("Project could not be loaded or processed: $exception")
      }
      finally {
        // Closes the project even if it is not fully opened, but started to
        Disposer.dispose(parentDisposable)
      }
    }

    private fun fatalError(msg: String): Nothing {
      System.err.println("Evaluation failed: $msg")
      exitProcess(1)
    }
  }

  inner class MainEvaluationCommand : EvaluationCommand(commandName, "Evaluate code completion quality in headless mode") {
    override fun run() = Unit
  }

  abstract class EvaluationCommandBase(name: String, help: String) : EvaluationCommand(name, help) {

    private val configPath by argument(name = "config-path", help = "Path to config").default(ConfigFactory.DEFAULT_CONFIG_NAME)

    override fun run() {
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the $featureName")
      val config = loadConfig(Paths.get(configPath), feature.getStrategySerializer())
      loadAndApply(config.projectPath) { project ->
        val workspace = EvaluationWorkspace.create(config)
        val stepFactory = BackgroundStepFactory(feature, config, project, null, EvaluationRootInfo(true))
        EvaluationProcess.build({
                                  customize()
                                  shouldReorderElements = config.reorder.useReordering
                                }, stepFactory).startAsync(workspace).get()
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
      val workspace = EvaluationWorkspace.open(workspacePath)
      val config = workspace.readConfig(feature.getStrategySerializer())
      loadAndApply(config.projectPath) { project ->
        val process = EvaluationProcess.build({
                                                shouldGenerateActions = false
                                                shouldInterpretActions = interpretActions
                                                shouldReorderElements = reorderElements
                                                shouldGenerateReports = generateReport
                                              }, BackgroundStepFactory(feature, config, project, null, EvaluationRootInfo(true)))
        process.startAsync(workspace)
      }
    }
  }

  abstract class MultipleEvaluationsBase(name: String, help: String) : EvaluationCommand(name, help) {

    abstract fun getWorkspaces(): List<String>

    override fun run() {
      val workspacesToCompare = getWorkspaces()
      val feature = EvaluableFeature.forFeature(featureName) ?: throw Exception("No support for the feature")
      val config = workspacesToCompare.map { EvaluationWorkspace.open(it) }.buildMultipleEvaluationsConfig(
        feature.getStrategySerializer(),
        "COMPARING",
      )
      val outputWorkspace = EvaluationWorkspace.create(config)
      loadAndApply(config.projectPath) { project ->
        val process = EvaluationProcess.build({
                                                shouldGenerateReports = true
                                              },
                                              BackgroundStepFactory(feature, config, project, workspacesToCompare, EvaluationRootInfo(true)))
        process.startAsync(outputWorkspace)
      }
    }
  }

  class MultipleEvaluations : MultipleEvaluationsBase(name = "multiple-evaluations", help = "Generate comparing report by multiple evaluations") {
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
      val config = workspacesToMerge.map { EvaluationWorkspace.open(it) }.buildMultipleEvaluationsConfig(
        feature.getStrategySerializer()
      )
      val outputWorkspace = EvaluationWorkspace.create(config)
      for (workspacePath in workspacesToMerge) {
        val workspace = EvaluationWorkspace.open(workspacePath)
        val sessionFiles = workspace.sessionsStorage.getSessionFiles()
        for (sessionFile in sessionFiles) {
          outputWorkspace.sessionsStorage.saveSessions(workspace.sessionsStorage.getSessions(sessionFile.first))
        }
      }
      outputWorkspace.saveMetadata()
      loadAndApply(config.projectPath) { project ->
        val process = EvaluationProcess.build({
                                                shouldGenerateReports = true
                                              },
                                              BackgroundStepFactory(feature, config, project, null, EvaluationRootInfo(true)))
        process.startAsync(outputWorkspace)
      }
    }
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
