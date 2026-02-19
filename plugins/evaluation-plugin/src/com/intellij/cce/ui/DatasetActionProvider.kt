package com.intellij.cce.ui

import com.intellij.cce.actions.ActionsBuilder
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface DatasetActionProvider {

  fun getActionName(): String
  fun callFeature(project: Project, session: ActionsBuilder.SessionBuilder)

  companion object {
    private val EP_NAME = ExtensionPointName.create<DatasetActionProvider>("com.intellij.cce.datasetActionProvider")

    fun getActions(): List<DatasetActionProvider> {
      return EP_NAME.extensionList
    }
  }
}