// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType

object DataContextUtils {
  /**
   * Returns language from [CommonDataKeys.PSI_FILE]
   * or by file type from [CommonDataKeys.VIRTUAL_FILE] or [PlatformCoreDataKeys.FILE_EDITOR]
   */
  @JvmStatic
  fun getFileLanguage(dataContext: DataContext): Language? {
    return CommonDataKeys.PSI_FILE.getData(dataContext)?.language ?: getFileTypeLanguage(dataContext)
  }

  /**
   * Returns language by file type from [CommonDataKeys.VIRTUAL_FILE] or [PlatformCoreDataKeys.FILE_EDITOR]
   */
  @JvmStatic
  private fun getFileTypeLanguage(dataContext: DataContext): Language? {
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
                      ?: PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)?.file ?: return null
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(virtualFile.nameSequence)
    if (fileType is LanguageFileType) {
      return fileType.language
    }
    return null
  }
}
