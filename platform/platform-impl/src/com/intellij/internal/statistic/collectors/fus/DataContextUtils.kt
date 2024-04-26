// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile

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
  fun getFileTypeLanguage(dataContext: DataContext): Language? {
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
                      ?: PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)?.file ?: return null
    return getLanguageByFileName(virtualFile)
  }

  /**
   * Returns language by file type from [PlatformCoreDataKeys.FILE_EDITOR]
   */
  @JvmStatic
  fun getFileTypeLanguageByEditor(dataContext: DataContext): Language? {
    val virtualFile = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)?.file ?: return null
    return getLanguageByFileName(virtualFile)
  }

  /**
   * Returns language of file type from file name
   */
  @JvmStatic
  private fun getLanguageByFileName(file: VirtualFile): Language? {
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence)
    if (fileType is LanguageFileType) {
      return fileType.language
    }
    return null
  }
}
