package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.completion.ml.ngram.NGramFileListener
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

class FactorsTrackerInitializer : ApplicationInitializedListener {
  override fun componentsInitialized() {
    val busConnection = ApplicationManager.getApplication().messageBus.connect()

    busConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        val lookupManager = LookupManager.getInstance(project)
        lookupManager.addPropertyChangeListener(CompletionFactorsInitializer(), project)
        project.messageBus.connect().subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, NGramFileListener(project))
      }
    })
  }

}