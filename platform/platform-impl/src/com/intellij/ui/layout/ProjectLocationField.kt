// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.comap
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

fun Row.projectLocationField(locationProperty: GraphProperty<String>,
                             wizardContext: WizardContext): CellBuilder<TextFieldWithBrowseButton> {
  val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter { it.isDirectory }
  val fileChosen = { file: VirtualFile -> getUiFilePath(file.path) }
  val title = IdeBundle.message("title.select.project.file.directory", wizardContext.presentationName)
  val property = locationProperty.map { getUiFilePath(it) }.comap { getModelFilePath(it) }
  return this.textFieldWithBrowseButton(property, title, wizardContext.project, fileChooserDescriptor, fileChosen)
}

internal fun getUiFilePath(path: String): String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

internal fun getModelFilePath(path: String): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()))
}