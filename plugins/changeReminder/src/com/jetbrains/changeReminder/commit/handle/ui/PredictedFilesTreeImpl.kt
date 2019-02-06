package com.jetbrains.changeReminder.commit.handle.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.jetbrains.changeReminder.predict.PredictedChange
import com.jetbrains.changeReminder.predict.PredictedFile
import com.jetbrains.changeReminder.predict.PredictedFilePath

class PredictedFilesTreeImpl(
  project: Project,
  showCheckboxes: Boolean = false,
  highlightProblems: Boolean = false,
  files: List<PredictedFile> = emptyList()
) : ChangesTreeImpl<PredictedFile>(project, showCheckboxes, highlightProblems, PredictedFile::class.java, files) {

  override fun buildTreeModel(changes: MutableList<out PredictedFile>) = TreeModelBuilder(myProject, grouping)
    .setChanges(changes.filterIsInstance<PredictedChange>().map { it.change }, null)
    .setFilePaths(changes.filterIsInstance<PredictedFilePath>().map { it.filePath })
    .build()
}