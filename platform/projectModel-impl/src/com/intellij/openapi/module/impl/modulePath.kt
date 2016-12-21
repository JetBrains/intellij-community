/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.util.PathUtil

data class ModulePath(val path: String, val group: String?) {
  /**
   * Module name (without file extension)
   */
  val moduleName: String = getModuleNameByFilePath(path)
}

fun getModuleNameByFilePath(path: String): String {
  return PathUtil.getFileName(path).removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION)
}