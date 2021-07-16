// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem

internal class VcsFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val IS_IGNORED_DATA_KEY = "isIgnored"
    private const val IS_CHANGED_DATA_KEY = "isChanged"
  }

  override fun getElementFeatures(element: Any, currentTime: Long, queryLength: Int): Map<String, Any> {
    if (element !is PSIPresentationBgRendererWrapper.PsiItemWithPresentation)
      return emptyMap()

    return getFileFeatures(element.item)
  }

  private fun getFileFeatures(element: PsiElement): Map<String, Any> {
    val virtualFile = (element as PsiFileSystemItem).virtualFile
    val project = element.project

    if (virtualFile.isDirectory) {
      return emptyMap()
    }

    return hashMapOf(
      IS_CHANGED_DATA_KEY to isChanged(virtualFile, project),
      IS_IGNORED_DATA_KEY to isIgnored(virtualFile, project),
    )
  }

  private fun isIgnored(virtualFile: VirtualFile, project: Project): Boolean {
    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isIgnoredFile(virtualFile)
  }

  private fun isChanged(virtualFile: VirtualFile, project: Project): Boolean {
    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isFileAffected(virtualFile)
  }
}
