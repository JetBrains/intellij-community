// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.mac.MacPathChooserDialog
import com.intellij.ui.win.WinPathChooserDialog
import java.awt.Component
import javax.swing.JTextField

interface ClientFileChooserFactory {
  companion object {
    @JvmStatic
    val instance: ClientFileChooserFactory
      get() = service()

    @JvmStatic
    fun useNewChooser(descriptor: FileChooserDescriptor): Boolean {
      return Registry.`is`("ide.ui.new.file.chooser") &&
             descriptor.roots.all({ f: VirtualFile -> f.isInLocalFileSystem })
    }

    @JvmStatic
    fun useNativeWinChooser(descriptor: FileChooserDescriptor): Boolean {
      return SystemInfo.isWindows &&
             !descriptor.isForcedToUseIdeaFileChooser &&
             Registry.`is`("ide.win.file.chooser.native", false)
    }

    @JvmStatic
    fun useNativeMacChooser(descriptor: FileChooserDescriptor): Boolean {
      return SystemInfo.isMac &&
             SystemInfo.isJetBrainsJvm &&
             !descriptor.isForcedToUseIdeaFileChooser &&
             Registry.`is`("ide.mac.file.chooser.native", true)
    }

    @JvmStatic
    fun createNativePathChooserIfEnabled(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): PathChooserDialog? {
      return if (useNativeMacChooser(descriptor)) {
        MacPathChooserDialog(descriptor, parent, project)
      }
      else if (useNativeWinChooser(descriptor)) {
        WinPathChooserDialog(descriptor, parent, project)
      }
      else null
    }

    @JvmStatic
    fun getMacroMap(): Map<String, String?> {
      val macros = PathMacros.getInstance()
      val allNames = macros.allMacroNames
      val map: MutableMap<String, String?> = HashMap(allNames.size)
      for (eachMacroName in allNames) {
        map["$$eachMacroName$"] = macros.getValue(eachMacroName)
      }
      return map
    }
  }

  fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog
  fun createPathChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): PathChooserDialog
  fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog
  fun createSaveFileDialog(descriptor: FileSaverDescriptor, parent: Component): FileSaverDialog
  fun createFileTextField(descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?): FileTextField
  fun installFileCompletion(field: JTextField, descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?)
}