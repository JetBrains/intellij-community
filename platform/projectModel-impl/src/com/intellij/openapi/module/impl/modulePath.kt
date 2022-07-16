// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.util.PathUtilRt

/**
 * Path here must be system-independent.
 */
data class ModulePath(val path: String, val group: String?) {
  /**
   * Module name (without file extension)
   */
  val moduleName: String = getModuleNameByFilePath(path)
}

fun getModuleNameByFilePath(path: String): String {
  return PathUtilRt.getFileName(path).removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION)
}