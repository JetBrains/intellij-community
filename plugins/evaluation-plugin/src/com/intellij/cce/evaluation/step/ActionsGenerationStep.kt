// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.ActionsGenerator
import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.FileActions
import com.intellij.cce.core.JvmProperties
import com.intellij.cce.core.PropertyAdapters
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.processor.DefaultEvaluationRootProcessor
import com.intellij.cce.processor.EvaluationRootByRangeProcessor
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.util.ExceptionsUtil.stackTraceToString
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.util.Progress
import com.intellij.cce.util.Summary
import com.intellij.cce.visitor.CodeFragmentBuilder
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

open class ActionsGenerationStep(
  protected val config: Config,
  protected val language: String,
  protected  val evaluationRootInfo: EvaluationRootInfo,
  project: Project,
  protected val processor: GenerateActionsProcessor,
  protected val featureName: String
) : BackgroundEvaluationStep(project) {
  override val name: String = "Generating actions"

  override val description: String = "Generating actions by selected files"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val filesForEvaluation = ReadAction.compute<List<VirtualFile>, Throwable> {
      FilesHelper.getFilesOfLanguage(project, config.actions.evaluationRoots, config.actions.ignoreFileNames, language)
    }
    generateActions(
      workspace,
      language,
      filesForEvaluation,
      evaluationRootInfo,
      progress,
      filesLimit = config.interpret.filesLimit ?: Int.MAX_VALUE,
      sessionsLimit = config.interpret.sessionsLimit ?: Int.MAX_VALUE,)
    return workspace
  }

  protected fun generateActions(
    workspace: EvaluationWorkspace,
    languageName: String,
    files: Collection<VirtualFile>,
    evaluationRootInfo: EvaluationRootInfo,
    indicator: Progress,
    filesLimit: Int = Int.MAX_VALUE,
    sessionsLimit: Int = Int.MAX_VALUE,
  ) {
    val actionsGenerator = ActionsGenerator(processor)
    val codeFragmentBuilder = CodeFragmentBuilder.create(project, languageName, featureName, config.strategy)

    val errors = mutableListOf<FileErrorInfo>()
    var totalSessions = 0
    var totalFiles = 0
    val actionsSummarizer = ActionsSummarizer()
    for ((i, file) in files.sortedBy { it.name }.withIndex()) {
      if (totalSessions >= sessionsLimit) {
        LOG.warn("Generating actions is canceled by sessions limit ($totalSessions).  With error: ${errors.size}")
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
        val fileActions = actionsGenerator.generate(codeFragment, sessionLimit = sessionsLimit - totalSessions)
        actionsSummarizer.update(fileActions)
        workspace.actionsStorage.saveActions(fileActions)
        totalSessions += fileActions.sessionsCount
        if (fileActions.sessionsCount > 0) {
          totalFiles++
        }
        indicator.setProgress(filename, "${totalSessions.toString().padStart(4)} sessions | $filename", progress)
      }
      catch (e: Throwable) {
        indicator.setProgress(filename, "error: ${e.message} | $filename", progress)
        try {
          workspace.errorsStorage.saveError(
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

    actionsSummarizer.save(workspace)
  }

  private class ActionsSummarizer {
    private val rootSummary: Summary = Summary.create()
    fun update(fileActions: FileActions) {
      rootSummary.apply {
        inc("files")
        group("actions") {
          for (action in fileActions.actions) {
            inc("total")
            inc(action.type.toString().toLowerCase())
          }
        }
        group("sessions") {
          for (action in fileActions.actions.filterIsInstance<CallFeature>()) {
            inc("total")
            val properties = action.nodeProperties
            group("common (frequent expected text by token type)") {
              countingGroup(properties.tokenType.name.toLowerCase(), 100) {
                inc(action.expectedText)
              }
            }
            val javaProperties = properties.java()
            if (javaProperties != null) {
              group("java (frequent tokens by kind)") {
                inc("total")
                inc(javaProperties.tokenType.toString().toLowerCase())
                group(action.kind().toString().toLowerCase()) {
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

    fun save(workspace: EvaluationWorkspace) {
      workspace.saveAdditionalStats("actions", rootSummary.asSerializable())
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
}
