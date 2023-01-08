package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.lang.Language
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.ui.components.labels.LinkLabel
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask

fun deleteCurrentModelLinkLabel(language: Language, actions: MutableList<SetupLocalModelsTask.ToDoParams>): LinkLabel<*> {
  return LinkLabel.create(message("fl.server.completion.models.delete"), null).apply {
    setListener({ _, _ ->
                  text = if (actions.find { it.action == SetupLocalModelsTask.Action.REMOVE } != null) {
                    actions.removeIf { it.action == SetupLocalModelsTask.Action.REMOVE }
                    message("fl.server.completion.models.delete")
                  }
                  else {
                    if (actions.find { it.action == SetupLocalModelsTask.Action.IMPORT_FROM_LOCAL_FILE } != null) {
                      actions.removeIf { it.action == SetupLocalModelsTask.Action.IMPORT_FROM_LOCAL_FILE }
                    }
                    actions.add(SetupLocalModelsTask.ToDoParams(language, SetupLocalModelsTask.Action.REMOVE))
                    message("fl.server.completion.models.delete.undo")
                  }
                }, null)
  }
}

fun modelFromLocalFileLinkLabel(language: Language, actions: MutableList<SetupLocalModelsTask.ToDoParams>): LinkLabel<*> {
  return LinkLabel.create(message("fl.server.completion.models.source.local")) {
    val file = FileChooser.chooseFile(
      FileChooserDescriptor(true, true, false, false, false, false), null, null
    )
    if (file != null) {
      if (actions.find { it.action == SetupLocalModelsTask.Action.IMPORT_FROM_LOCAL_FILE } != null) {
        actions.removeIf { it.action == SetupLocalModelsTask.Action.IMPORT_FROM_LOCAL_FILE }
      }

      actions.add(
        SetupLocalModelsTask.ToDoParams(
          language,
          SetupLocalModelsTask.Action.IMPORT_FROM_LOCAL_FILE,
          file.path
        )
      )
    }
  }
}
