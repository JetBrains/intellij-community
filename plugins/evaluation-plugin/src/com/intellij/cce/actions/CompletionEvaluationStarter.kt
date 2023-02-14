package com.intellij.cce.actions

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.util.ExceptionsUtil.stackTraceToString
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.io.isDirectory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.system.exitProcess

internal class CompletionEvaluationStarter : ApplicationStarter {
  override val commandName: String
    get() = "ml-evaluate"

  override fun main(args: List<String>) {
    MainEvaluationCommand()
      .subcommands(FullCommand(), GenerateActionsCommand(), CustomCommand(),
                   MultipleEvaluations(), CompareEvaluationsInDirectory())
      .main(args.toList().subList(1, args.size))
  }

  abstract class EvaluationCommand(name: String, help: String) : CliktCommand(name = name, help = help) {
    protected fun loadConfig(configPath: Path) = try {
      ConfigFactory.load(configPath)
    }
    catch (e: Exception) {
      fatalError("Error for loading config: $configPath, $e. StackTrace: ${stackTraceToString(e)}")
    }

    protected fun loadProject(projectPath: String): Project = try {
      println("Open and load project $projectPath. Operation may take few minutes.")
      val project = OpenProjectMethodProvider.find()?.openProjectInHeadlessMode(projectPath) ?: openProjectHeadless(projectPath)
      println("Project loaded!")
      project
    }
    catch (e: Throwable) {
      fatalError("Project could not be loaded: $e")
    }

    private fun fatalError(msg: String): Nothing {
      System.err.println("Evaluation failed: $msg")
      exitProcess(1)
    }

    private fun openProjectHeadless(projectPath: String): Project {
      val project = File(projectPath)
      assert(project.exists()) { "File $projectPath does not exist" }

      val projectDir = if (project.isDirectory) project else project.parentFile
      val ideaDir = File(projectDir, Project.DIRECTORY_STORE_FOLDER)
      if (!ideaDir.exists()) {
        println(".idea directory is missing. Project will be imported")
        val importedProject = ProjectUtil.openOrImport(project.toPath(), null, false)
        assert(importedProject != null) { ".idea directory is missing and project can't be imported" }
        return importedProject!!
      }
      val existing = ProjectManager.getInstance().openProjects.firstOrNull { proj ->
        !proj.isDefault && ProjectUtil.isSameProject(project.toPath(), proj)
      }
      if (existing != null) return existing

      return ProjectManager.getInstance().loadAndOpenProject(projectPath)!!
    }
  }

  inner class MainEvaluationCommand : EvaluationCommand(commandName, "Evaluate code completion quality in headless mode") {
    override fun run() = Unit
  }

  abstract class EvaluationCommandBase(name: String, help: String) : EvaluationCommand(name, help) {
    private val configPath by argument(name = "config-path", help = "Path to config").default(ConfigFactory.DEFAULT_CONFIG_NAME)

    override fun run() {
      val config = loadConfig(Paths.get(configPath))
      val project = loadProject(config.projectPath)
      val workspace = EvaluationWorkspace.create(config)
      val stepFactory = BackgroundStepFactory(config, project, true, null, EvaluationRootInfo(true))
      EvaluationProcess.build({
                                customize()
                                shouldReorderElements = config.reorder.useReordering
                              }, stepFactory).startAsync(workspace)
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
      val workspace = EvaluationWorkspace.open(workspacePath)
      val config = workspace.readConfig()
      val project = loadProject(config.projectPath)
      val process = EvaluationProcess.build({
                                              shouldGenerateActions = false
                                              shouldInterpretActions = interpretActions
                                              shouldReorderElements = reorderElements
                                              shouldGenerateReports = generateReport
                                            }, BackgroundStepFactory(config, project, true, null, EvaluationRootInfo(true)))
      process.startAsync(workspace)
    }
  }

  abstract class MultipleEvaluationsBase(name: String, help: String) : EvaluationCommand(name, help) {
    abstract fun getWorkspaces(): List<String>

    override fun run() {
      val workspacesToCompare = getWorkspaces()
      val config = workspacesToCompare.map { EvaluationWorkspace.open(it) }.buildMultipleEvaluationsConfig()
      val outputWorkspace = EvaluationWorkspace.create(config)
      val project = loadProject(config.projectPath)
      val process = EvaluationProcess.build({
                                              shouldGenerateReports = true
                                            }, BackgroundStepFactory(config, project, true, workspacesToCompare, EvaluationRootInfo(true)))
      process.startAsync(outputWorkspace)
    }
  }

  class MultipleEvaluations : MultipleEvaluationsBase(name = "multiple-evaluations", help = "Generate report by multiple evaluations") {
    private val workspacesArg by argument(name = "workspaces", help = "List of workspaces").multiple()

    override fun getWorkspaces(): List<String> = workspacesArg
  }

  class CompareEvaluationsInDirectory : MultipleEvaluationsBase(name = "compare-in",
                                                                help = "Generate report for all evaluation workspaces in a directory") {
    private val root by argument(name = "directory", help = "Root directory for evaluation workspaces")

    override fun getWorkspaces(): List<String> {
      val outputDirectory = Paths.get(root)
      if (!outputDirectory.exists() || !outputDirectory.isDirectory()) {
        throw BadParameterValue("Directory \"$root\" not found.")
      }

      val nestedFiles = outputDirectory.toFile().listFiles() ?: emptyArray()

      val result = nestedFiles.filter { it.isDirectory }.map { it.absolutePath }
      if (result.isEmpty()) {
        throw BadParameterValue("Directory \"$root\" should not be empty")
      }

      return result
    }
  }
}
