package org.jetbrains.completion.full.line.tasks

import com.intellij.lang.Language
import com.intellij.openapi.application.EdtReplacementThread
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.completion.full.line.currentOpenProject
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import java.util.concurrent.atomic.AtomicBoolean

class SetupLocalModelsTask(project: Project, private val languagesToDo: List<ToDoParams>) : Task.Backgroundable(
  project,
  message("full.line.progress.downloading.models.title"),
) {
  val log = thisLogger()

  constructor(project: Project, vararg languagesToDo: ToDoParams) : this(project, languagesToDo.toList())

  private val manager = service<ConfigurableModelsManager>()

  @Synchronized
  override fun run(indicator: ProgressIndicator) {
    isRunning.set(true)
    languagesToDo.forEach { (language, action, modelPath) ->
      when (action) {
        Action.DOWNLOAD -> {
          title = "Downloading full line model for ${language.displayName}"
          log.info("Downloading full line model for ${language.displayName}")
          manager.download(language, true)
        }
        Action.UPDATE -> {
          title = "Updating full line model for ${language.displayName}"
          log.info("Updating full line model for ${language.displayName}")
          manager.update(language, true)
        }
        Action.REMOVE -> {
          title = "Removing full line model for ${language.displayName}"
          log.info("Removing full line model for ${language.displayName}")
          manager.remove(language)
        }
        Action.IMPORT_FROM_LOCAL_FILE -> {
          requireNotNull(modelPath) { "For importing local file modelPath must be provided" }

          title = "Importing full line model from local file for ${language.displayName}"
          log.info("Importing full line model from local file for ${language.displayName}")
          manager.importLocal(language, modelPath)
        }
      }
    }
  }

  override fun onSuccess() = manager.apply()
  override fun onCancel() = manager.reset()
  override fun onThrowable(error: Throwable) = manager.reset()
  override fun onFinished() = isRunning.set(false)
  override fun whereToRunCallbacks() = EdtReplacementThread.WT
  override fun shouldStartInBackground() = true

  enum class Action {
    IMPORT_FROM_LOCAL_FILE,
    DOWNLOAD,
    UPDATE,
    REMOVE,
  }

  data class ToDoParams(
    val language: Language,
    val action: Action,
    // required for local
    val modelPath: String? = null
  )

  companion object {
    val isRunning: AtomicBoolean = AtomicBoolean(false)

    fun queue(actions: List<ToDoParams>) {
      // TODO: handle when project hasn't been created yet
      val project = ProjectManager.getInstanceIfCreated()?.currentOpenProject() ?: return

      // Downloading new and removing old models
      SetupLocalModelsTask(project, actions).queue()
    }
  }
}
