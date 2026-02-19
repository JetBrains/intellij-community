// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle

private const val DEVKIT_APPLICATION_SERVICE_TEMPLATE = "DevKit Application Service"
private const val DEVKIT_PROJECT_SERVICE_TEMPLATE = "DevKit Project Service"

internal class NewServiceAction : CreateFileFromTemplateAction() {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder
      .setTitle(DevKitBundle.message("dialog.title.new.devkit.service"))
      .addKind(DevKitBundle.message("list.item.applications.service"), DevkitCoreIcons.Service, DEVKIT_APPLICATION_SERVICE_TEMPLATE)
      .addKind(DevKitBundle.message("list.item.project.service"), DevkitCoreIcons.ProjectService, DEVKIT_PROJECT_SERVICE_TEMPLATE)
      .setDefaultText("MyService")
  }

  override fun getActionName(directory: PsiDirectory?, newName: @NonNls String, templateName: @NonNls String?): @NlsContexts.Command String? {
    return DevKitBundle.message("action.name.new.devkit.service")
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    return super.isAvailable(dataContext) && isDevKitClassTemplateAvailable(dataContext)
  }
}