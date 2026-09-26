// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.util

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls

object EditorConfigPresentationUtil {
  @JvmStatic
  fun getFileName(file: PsiFile, withFolder: Boolean): String {
    return getFileName(file.virtualFile, withFolder)
  }

  @JvmStatic
  @NlsSafe
  fun getFileName(file: VirtualFile, withFolder: Boolean): String {
    val settings = UISettings.instanceOrNull
    val settingsAwareFlag = settings?.state?.showDirectoryForNonUniqueFilenames?.and(withFolder) ?: withFolder
    return if (settingsAwareFlag) "${file.parent?.name}/${file.name}" else file.name
  }

  @JvmStatic
  fun path(element: PsiElement): @NonNls String = EditorConfigPsiTreeUtil.getOriginalFile(element.containingFile)?.virtualFile?.parent?.path ?: ""
}
