package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.*
import com.intellij.cce.core.*
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.processor.DefaultEvaluationRootProcessor
import com.intellij.cce.processor.EvaluationRootByRangeProcessor
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

class ActionsGenerationStep(
  private val config: Config.ActionsGeneration,
  private val language: String,
  private val evaluationRootInfo: EvaluationRootInfo,
  project: Project,
  isHeadless: Boolean) : BackgroundEvaluationStep(project, isHeadless) {
  override val name: String = "Generating actions"

  override val description: String = "Generating actions by selected files"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val filesForEvaluation = ReadAction.compute<List<VirtualFile>, Throwable> { FilesHelper.getFilesOfLanguage(project, config.evaluationRoots, language) }
    generateActions(workspace, language, filesForEvaluation, config.strategy, evaluationRootInfo, progress)
    return workspace
  }

  private fun generateActions(workspace: EvaluationWorkspace, languageName: String, files: Collection<VirtualFile>,
                              strategy: CompletionStrategy, evaluationRootInfo: EvaluationRootInfo, indicator: Progress) {
    val actionsGenerator = ActionsGenerator(strategy, Language.resolve(languageName))
    val codeFragmentBuilder = CodeFragmentBuilder.create(project, languageName, strategy.completionGolf)

    val errors = mutableListOf<FileErrorInfo>()
    var totalSessions = 0
    val actionsSummarizer = ActionsSummarizer()
    for ((i, file) in files.sortedBy { it.name }.withIndex()) {
      if (indicator.isCanceled()) {
        LOG.info("Generating actions is canceled by user. Done: $i/${files.size}. With error: ${errors.size}")
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
        val codeFragment = codeFragmentBuilder.build(file, rootVisitor)
        val fileActions = actionsGenerator.generate(codeFragment)
        actionsSummarizer.update(fileActions)
        workspace.actionsStorage.saveActions(fileActions)
        totalSessions += fileActions.sessionsCount
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
          for (action in fileActions.actions.asSessionStarts()) {
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

    private fun List<Action>.asSessionStarts(): Iterable<CallCompletion> {
      val result = mutableListOf<CallCompletion>()
      var insideSession = false
      for (action in this) {
        if (!insideSession && action is CallCompletion) {
          result.add(action)
          insideSession = true
        }
        else if (action is FinishSession) {
          insideSession = false
        }
      }

      return result
    }

    fun save(workspace: EvaluationWorkspace) {
      workspace.saveAdditionalStats("actions", rootSummary.asSerializable())
    }

    private enum class CompletionKind {
      PROJECT, JRE, THIRD_PARTY, UNKNOWN_LOCATION, UNKNOWN_PACKAGE
    }

    private fun CallCompletion.kind(): CompletionKind {
      if (nodeProperties.location == SymbolLocation.PROJECT) return CompletionKind.PROJECT
      if (nodeProperties.location == SymbolLocation.UNKNOWN) return CompletionKind.UNKNOWN_LOCATION
      val packageName = nodeProperties.java()?.packageName ?: return CompletionKind.UNKNOWN_PACKAGE
      return if (packageName.startsWith("java")) CompletionKind.JRE else CompletionKind.THIRD_PARTY
    }

    private fun TokenProperties.java(): JvmProperties? = PropertyAdapters.Jvm.adapt(this)
  }
}
