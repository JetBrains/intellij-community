// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.devkit.apiDump.ApiDumpUtil
import com.intellij.devkit.apiDump.DevkitApiDumpIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal object ADFileType : LanguageFileType(ADLanguage), FileTypeIdentifiableByVirtualFile {
  override fun getName(): @NonNls String = "ADLanguage"

  override fun getDescription(): @NlsContexts.Label String = ApiDumpLangBundle.message("label.apidump.language.file")

  override fun getDefaultExtension(): @NlsSafe String = ""

  override fun getIcon(): Icon = DevkitApiDumpIcons.ApiDump

  override fun isMyFileType(file: VirtualFile): Boolean =
    ApiDumpUtil.isApiDumpFile(file) ||
    ApiDumpUtil.isApiDumpUnreviewedFile(file) ||
    ApiDumpUtil.isApiDumpExperimentalFile(file)
}