// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.common.CommonActionsInvoker
import com.intellij.cce.evaluation.*
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.interpreter.*
import com.intellij.cce.processor.DefaultEvaluationRootProcessor
import com.intellij.cce.processor.EvaluationRootByRangeProcessor
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.util.ExceptionsUtil.stackTraceToString
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.util.Progress
import com.intellij.cce.util.Summary
import com.intellij.cce.util.text
import com.intellij.cce.visitor.CodeFragmentBuilder
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.storages.storage.ActionsSingleFileStorage
import com.intellij.configurationStore.StoreUtil.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import kotlin.random.Random

open class ProjectActionsEnvironment(
  private val strategy: EvaluationStrategy,
  val config: Config.ActionsGeneration,
  private val filesLimit: Int?, // TODO dataset generation could be lazy
  private val sessionsLimit: Int?,
  private val evaluationRootInfo: EvaluationRootInfo,
  val project: Project,
  val processor: GenerateActionsProcessor,
  override val setupSteps: List<EvaluationStep>,
  private val featureName: String,
  val featureInvoker: FeatureInvoker,
) : EvaluationEnvironment {
  private val datasetRef = config.sourceFile.run {
    val sf = this ?: ""
    if (sf.isNotBlank()) {
      DatasetRef.parse(sf)
    } else {
      null
    }
  }

  override val preparationDescription: String = "Generating actions by selected files"

  override fun initialize(datasetContext: DatasetContext) {
    datasetRef?.prepare(datasetContext)
  }

  override fun prepareDataset(datasetContext: DatasetContext, progress: Progress) {
    if (datasetRef != null) {
      val finalPath = DatasetRefConverter().convert(datasetRef, datasetContext, project) ?: datasetContext.path(datasetRef)
      datasetContext.replaceActionsStorage(ActionsSingleFileStorage(finalPath))
    } else {
      val filesForEvaluation = ReadAction.compute<List<VirtualFile>, Throwable> {
        FilesHelper.getFilesOfLanguage(project, config.evaluationRoots, config.ignoreFileNames, config.language)
      }

      generateActions(
        datasetContext,
        config.language,
        filesForEvaluation,
        evaluationRootInfo,
        progress,
        filesLimit = this.filesLimit ?: Int.MAX_VALUE,
        sessionsLimit = this.sessionsLimit ?: Int.MAX_VALUE,
      )
    }
  }

  override fun sessionCount(datasetContext: DatasetContext): Int {
    return datasetContext.actionsStorage.computeSessionsCount()
  }

  override fun chunks(datasetContext: DatasetContext): Sequence<EvaluationChunk> {
    val files = datasetContext.actionsStorage.getActionFiles()
    return files.shuffled(FILES_RANDOM).asSequence().map { file ->
      val fileActions = datasetContext.actionsStorage.getActions(file)
      val fileText = FilesHelper.getFile(project, fileActions.path).text()
      FileActionsChunk(fileActions, fileText)
    }
  }

  protected fun generateActions(
    datasetContext: DatasetContext,
    languageName: String?,
    files: Collection<VirtualFile>,
    evaluationRootInfo: EvaluationRootInfo,
    indicator: Progress,
    filesLimit: Int = Int.MAX_VALUE,
    sessionsLimit: Int = Int.MAX_VALUE,
  ) {
    val actionsGenerator = ActionsGenerator(processor)
    val codeFragmentBuilder = CodeFragmentBuilder.create(project, languageName, featureName, strategy)

    val errors = mutableListOf<FileErrorInfo>()
    var totalSessions = 0
    var totalFiles = 0
    val actionsSummarizer = ActionsSummarizer()
    for ((i, file) in files.sortedBy { it.name }.shuffled(FILES_RANDOM).withIndex()) {
      if (totalSessions >= sessionsLimit) {
        LOG.warn("Generating actions is canceled by sessions limit. Sessions=$totalSessions, sessionsLimit=$sessionsLimit.  With error: ${errors.size}")
        break
      }
      if (totalFiles >= filesLimit) {
        LOG.warn("Generating actions is canceled by files limit ($totalFiles). Done: $i/${files.size}. With error: ${errors.size}")
        break
      }
      if (indicator.isCanceled()) {
        LOG.warn("Generating actions is canceled by user. Done: $i/${files.size}. With error: ${errors.size}")
        break
      }
      LOG.info("Start generating actions for file ${file.path}. Done: $i/${files.size}. With error: ${errors.size}")
      val filename = file.name
      val progress = (i + 1).toDouble() / files.size
      try {
        val rootVisitor = when {
          evaluationRootInfo.useDefault -> DefaultEvaluationRootProcessor()
          evaluationRootInfo.parentPsi != null -> EvaluationRootByRangeProcessor(
            evaluationRootInfo.parentPsi.textRange?.startOffset ?: evaluationRootInfo.parentPsi.textOffset,
            evaluationRootInfo.parentPsi.textRange?.endOffset
            ?: evaluationRootInfo.parentPsi.textOffset + evaluationRootInfo.parentPsi.textLength)
          else -> throw IllegalStateException("Parent psi and offset are null.")
        }
        val codeFragment = codeFragmentBuilder.build(file, rootVisitor, featureName)
        if (codeFragment == null) continue
        val fileActions = actionsGenerator.generate(codeFragment)
        actionsSummarizer.update(fileActions)
        datasetContext.actionsStorage.saveActions(fileActions)
        totalSessions += fileActions.sessionsCount
        if (fileActions.sessionsCount > 0) {
          totalFiles++
        }
        indicator.setProgress(filename, "${totalSessions.toString().padStart(4)} sessions | $filename", progress)
      }
      catch (e: Throwable) {
        indicator.setProgress(filename, "error: ${e.message} | $filename", progress)
        try {
          datasetContext.errorsStorage.saveError(
            FileErrorInfo(FilesHelper.getRelativeToProjectPath(project, file.path), e.message
                                                                                    ?: "No Message", stackTraceToString(e))
          )
        }
        catch (e2: Throwable) {
          LOG.error("Exception on saving error info for file ${file.path}.", e2)
        }
        LOG.error("Generating actions error for file ${file.path}.", e)
      }

      LOG.info("Generating actions for file ${file.path} completed. Done: $i/${files.size}. With error: ${errors.size}")
    }

    actionsSummarizer.save(datasetContext)
  }

  override fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace? =
    step.runInIntellij(project, workspace)

  override fun close() {
    ProjectOpeningUtils.closeProject(project)

    // Guarantee saving updated registries and settings on disc
    saveSettings(ApplicationManager.getApplication())
  }

  private class ActionsSummarizer {
    private val rootSummary: Summary = Summary.create()
    fun update(fileActions: FileActions) {
      rootSummary.apply {
        inc("files")
        group("actions") {
          for (action in fileActions.actions) {
            inc("total")
            inc(action.type.toString().lowercase(Locale.getDefault()))
          }
        }
        group("sessions") {
          for (action in fileActions.actions.filterIsInstance<CallFeature>()) {
            inc("total")
            val properties = action.nodeProperties
            group("common (frequent expected text by token type)") {
              countingGroup(properties.tokenType.name.lowercase(Locale.getDefault()), 100) {
                inc(action.expectedText)
              }
            }
            val javaProperties = properties.java()
            if (javaProperties != null) {
              group("java (frequent tokens by kind)") {
                inc("total")
                inc(javaProperties.tokenType.toString().lowercase(Locale.getDefault()))
                group(action.kind().toString().lowercase(Locale.getDefault())) {
                  inc("total")
                  if (javaProperties.isStatic) inc("static")
                  else inc("nonstatic")

                  countingGroup("popular symbols", 3000) {
                    inc("${javaProperties.containingClass}#${action.expectedText}")
                  }
                }
              }
            }
          }
        }
      }
    }

    fun save(datasetContext: DatasetContext) {
      datasetContext.saveAdditionalStats("actions", rootSummary.asSerializable())
    }

    private enum class CompletionKind {
      PROJECT, JRE, THIRD_PARTY, UNKNOWN_LOCATION, UNKNOWN_PACKAGE
    }

    private fun CallFeature.kind(): CompletionKind {
      if (nodeProperties.location == SymbolLocation.PROJECT) return CompletionKind.PROJECT
      if (nodeProperties.location == SymbolLocation.UNKNOWN) return CompletionKind.UNKNOWN_LOCATION
      val packageName = nodeProperties.java()?.packageName ?: return CompletionKind.UNKNOWN_PACKAGE
      return if (packageName.startsWith("java")) CompletionKind.JRE else CompletionKind.THIRD_PARTY
    }

    private fun TokenProperties.java(): JvmProperties? = PropertyAdapters.Jvm.adapt(this)
  }

  private inner class FileActionsChunk(
    private val fileActions: FileActions,
    private val presentationText: String,
  ) : EvaluationChunk {
    override val datasetName: String = config.projectName
    override val name: String = fileActions.path
    override val sessionsExist: Boolean = fileActions.sessionsCount > 0

    override fun evaluate(
      handler: InterpretationHandler,
      filter: InterpretFilter,
      order: InterpretationOrder,
      sessionHandler: (Session) -> Unit
    ): EvaluationChunk.Result {
      val factory = object : InvokersFactory {
        override fun createActionsInvoker(): ActionsInvoker = CommonActionsInvoker(project)
        override fun createFeatureInvoker(): FeatureInvoker = featureInvoker
      }
      val actionInterpreter = ActionInvokingInterpreter(factory, handler, filter, order)
      return EvaluationChunk.Result(
        actionInterpreter.interpret(fileActions, sessionHandler),
        presentationText
      )
    }
  }

  companion object {
    fun<T> open(projectPath: String, init: (Project) -> T): T {
      val project = ProjectOpeningUtils.open(projectPath)

      val environment = try {
        init(project)
      }
      catch (exception: Exception) {
        ProjectOpeningUtils.closeProject(project)
        throw RuntimeException("Failed to initialize project environment: $exception", exception)
      }

      return environment
    }
  }
}

private val LOG = Logger.getInstance(ProjectActionsEnvironment::class.java)
private val FILES_RANDOM = Random(42)
