// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JTextField

@ApiStatus.Internal
interface ClientFileChooserFactory {
  fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog
  fun createPathChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): PathChooserDialog
  fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog
  fun createSaveFileDialog(descriptor: FileSaverDescriptor, parent: Component): FileSaverDialog
  fun createFileTextField(descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?): FileTextField
  fun installFileCompletion(field: JTextField, descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?)
}
