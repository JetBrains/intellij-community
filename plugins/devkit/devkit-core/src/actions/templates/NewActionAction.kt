// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle

private const val DEVKIT_ACTION_TEMPLATE = "DevKit Action"
private const val DEVKIT_TOGGLE_ACTION_TEMPLATE = "DevKit Toggle Action"

internal class NewActionAction : CreateFileFromTemplateAction() {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder
      .setTitle(DevKitBundle.message("dialog.title.new.devkit.action"))
      .addKind(DevKitBundle.message("list.item.an.action"), AllIcons.Actions.RunAnything, DEVKIT_ACTION_TEMPLATE)
      .addKind(DevKitBundle.message("list.item.toggle.action"), AllIcons.Actions.Checked, DEVKIT_TOGGLE_ACTION_TEMPLATE)
      .setDefaultText("MyAction")
  }

  override fun getActionName(directory: PsiDirectory?, newName: @NonNls String, templateName: @NonNls String?): @NlsContexts.Command String? {
    return DevKitBundle.message("action.name.new.action")
  }

  override fun isAvailable(dataContext: DataContext): Boolean {
    return super.isAvailable(dataContext) && isDevKitClassTemplateAvailable(dataContext)
  }
}