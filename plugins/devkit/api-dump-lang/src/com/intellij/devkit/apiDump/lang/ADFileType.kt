// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.devkit.apiDump.icons.DevkitApiDumpIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal object ADFileType : LanguageFileType(ADLanguage) {
  override fun getName(): @NonNls String = "ADLanguage"

  override fun getDescription(): @NlsContexts.Label String = ApiDumpLangBundle.message("label.apidump.language.file")

  override fun getDefaultExtension(): @NlsSafe String = ""

  override fun getIcon(): Icon = DevkitApiDumpIcons.ApiDump
}