// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.devkit.apiDump.lang.ADFileType
import com.intellij.devkit.apiDump.lang.ADLanguage

internal class ADFileImpl(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ADLanguage) {
  override fun getFileType(): FileType = ADFileType.INSTANCE
}