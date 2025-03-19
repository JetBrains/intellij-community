// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions.templates

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.jps.model.java.JavaSourceRootType

internal fun isDevKitClassTemplateAvailable(dataContext: DataContext): Boolean {
  val module = dataContext.getData(PlatformCoreDataKeys.MODULE)
  if (module == null || !PsiUtil.isPluginModule(module)) {
    return false
  }

  val project = CommonDataKeys.PROJECT.getData(dataContext)
  val view = LangDataKeys.IDE_VIEW.getData(dataContext)
  if (project == null || view == null) {
    return false
  }

  val directories: Array<PsiDirectory> = view.getDirectories()
  if (directories.isEmpty()) return false

  val index = ProjectRootManager.getInstance(project).getFileIndex()
  val underRoot = directories.asSequence()
    .map { obj -> obj.virtualFile }
    .any { index.isUnderSourceRootOfType(it, setOf(JavaSourceRootType.SOURCE)) }

  return underRoot
}