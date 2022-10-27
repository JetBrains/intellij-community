// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.ConfigurationErrorType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.Nls

internal class IdeErrorReporter(private val project: Project) : ErrorReporter {
  val errors = ArrayList<ConfigurationErrorDescription>()

  override fun reportError(message: String, file: VirtualFileUrl) {
    if (FileUtil.extensionEquals(file.fileName, "iml")) {
      errors.add(ModuleLoadingErrorDescriptionBridge(message, file, project))
    }
    else {
      LOG.error("Failed to load ${file.presentableUrl}: $message")
    }
  }

  companion object {
    private val LOG = logger<IdeErrorReporter>()
  }
}

private object ModuleErrorType : ConfigurationErrorType(false) {

  override fun getErrorText(errorCount: Int, firstElementName: @NlsSafe String?): @Nls String {
    return ProjectModelBundle.message("module.configuration.problem.text", errorCount, firstElementName)
  }
}

private class ModuleLoadingErrorDescriptionBridge(@NlsContexts.DetailedDescription description: String,
                                                  private val moduleFile: VirtualFileUrl,
                                                  private val project: Project)
  : ConfigurationErrorDescription(FileUtil.getNameWithoutExtension(moduleFile.fileName), description) {

  override fun ignoreInvalidElement() {
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel("Remove invalid module: $elementName") { builder ->
        val moduleEntity = builder.resolve(ModuleId(elementName))
        if (moduleEntity != null) {
          builder.removeEntity(moduleEntity)
        }
      }
    }
  }

  override fun getIgnoreConfirmationMessage(): String {
    return ProjectModelBundle.message("module.remove.from.project.confirmation", elementName)
  }

  override fun getErrorType(): ModuleErrorType = ModuleErrorType
}