// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.SmartList
import com.intellij.util.containers.SmartHashSet

private val EP_NAME: ExtensionPointName<FileTypeInspectionDisabler> =
  ExtensionPointName.create("org.intellij.groovy.inspectionDisabler")

/**
 * @return A list of file types where inspections of class [clazz] are disabled by default.
 */
fun getDisableableFileTypes(clazz : Class<out LocalInspectionTool>) : List<FileType> {
  val list = SmartList<FileType>()
  for (disabler in EP_NAME.extensionList) {
    val disabled = disabler.getDisableableInspections()
    for ((fileType, inspections) in disabled) {
      if (clazz in inspections) {
        list.add(fileType)
      }
    }
  }
  return list
}

internal fun getDisableableFileNames(clazz: Class<out LocalInspectionTool>): MutableSet<String> =
  getDisableableFileTypes(clazz).mapTo(SmartHashSet()) { it.name }

interface FileTypeInspectionDisabler {
  fun getDisableableInspections(): Map<out FileType, Set<Class<out LocalInspectionTool>>>
}