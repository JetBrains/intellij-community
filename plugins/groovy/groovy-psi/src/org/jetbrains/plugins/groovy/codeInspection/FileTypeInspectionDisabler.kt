// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.util.containers.SmartHashSet

@Service(Service.Level.APP)
class FileTypeInspectionDisablers : FileTypeExtension<FileTypeInspectionDisabler>("org.intellij.groovy.inspectionDisabler")

/**
 * @return A list of file types where inspections of class [clazz] are disabled by default.
 */
fun getDisableableFileTypes(clazz : Class<out LocalInspectionTool>) : Set<FileType> {
  val allDisablers : Map<FileType, FileTypeInspectionDisabler> =
    ApplicationManager.getApplication().getService(FileTypeInspectionDisablers::class.java).allRegisteredExtensions
  return allDisablers.filterValues { disabler -> disabler.getDisableableInspections().contains(clazz) }.keys
}

internal fun getDisableableFileNames(clazz: Class<out LocalInspectionTool>): MutableSet<String> =
  getDisableableFileTypes(clazz).mapTo(SmartHashSet()) { it.name }

interface FileTypeInspectionDisabler {
  fun getDisableableInspections(): Set<Class<out LocalInspectionTool>>
}