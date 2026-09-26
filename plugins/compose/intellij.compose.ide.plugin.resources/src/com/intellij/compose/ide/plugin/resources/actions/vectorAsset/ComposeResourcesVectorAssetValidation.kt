// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.DrawableDir
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Page
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Source
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Validation
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.ValidationSeverity
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import kotlin.io.path.exists

internal fun ComposeResourcesVectorAssetDialog.updateValidation(validation: Validation? = null) {
  val isSecondPage = currentPage == Page.OUTPUT
  val formError = if (isSecondPage) validateSecondPage() else validateFirstPage()
  val displayValidation = formError ?: validation
  if (displayValidation != null) {
    validationPanel.showMessage(displayValidation)
    isOKActionEnabled = displayValidation.severity == ValidationSeverity.WARNING
  }
  else {
    validationPanel.clear()
    isOKActionEnabled = isSecondPage || fileLoadedProperty.get()
  }
}

internal fun ComposeResourcesVectorAssetDialog.validateFirstPage(): Validation? {
  val sourcePath = localFilePathProperty.get()
  if (currentSource is Source.LocalFile) {
    if (sourcePath.isNotBlank() && !isValidLocalFile(sourcePath)) {
      return Validation(ComposeIdeBundle.message("compose.vector.asset.validation.path.not.exist.error"), ValidationSeverity.ERROR)
    }
    if (sourcePath.isBlank()) return Validation(ComposeIdeBundle.message("compose.vector.asset.validation.no.file.error"),
                                                ValidationSeverity.ERROR)
  }

  if (!fileLoadedProperty.get()) return null

  if (widthProperty.get() <= 0) return Validation(ComposeIdeBundle.message("compose.vector.asset.validation.width.positive.error"),
                                                  ValidationSeverity.ERROR)
  if (heightProperty.get() <= 0) return Validation(ComposeIdeBundle.message("compose.vector.asset.validation.height.positive.error"),
                                                   ValidationSeverity.ERROR)
  val fileName = fileNameProperty.get()
  if (fileName.isEmpty()) return null

  val invalidChar = fileName.firstOrNull { c -> !(c in 'a'..'z' || c in '0'..'9' || c == '_') }
  if (invalidChar == null) return null

  return Validation(
    ComposeIdeBundle.message("compose.vector.asset.validation.name.validation.error", invalidChar),
    ValidationSeverity.ERROR
  )
}

internal fun ComposeResourcesVectorAssetDialog.validateSecondPage(): Validation? {
  val selectedDir = selectedDirProperty.get()
                    ?: return Validation(ComposeIdeBundle.message("compose.vector.asset.validation.no.output.directory.error"),
                                         ValidationSeverity.ERROR)

  val outputFileName = outputFileName ?: return null
  val fileNameWithExt = "$outputFileName.xml"

  fun DrawableDir.hasFile() = drawablePath.resolve(fileNameWithExt).exists()

  // Check if it exists in the selected directory
  if (selectedDir.hasFile()) return Validation(
    ComposeIdeBundle.message("compose.vector.asset.validation.file.exists.warning", outputFileName),
    ValidationSeverity.WARNING
  )

  val dirsWithFile = drawableDirs.filter { it.hasFile() }
  val existsInCommonMain = dirsWithFile.any { it.resourceDir.sourceSetName == "commonMain" }
  val platformSetsWithFile = dirsWithFile
    .filter { it.resourceDir.sourceSetName != "commonMain" }
    .map { it.resourceDir.sourceSetName }
  val isAddingToCommonMain = selectedDir.resourceDir.sourceSetName == "commonMain"

  val conflictingSourceSets = when {
    // Check if it exists in any other platform source set
    isAddingToCommonMain && platformSetsWithFile.isNotEmpty() -> platformSetsWithFile
    // Check if it exists in commonMain
    !isAddingToCommonMain && existsInCommonMain -> listOf("commonMain")
    else -> emptyList()
  }

  if (conflictingSourceSets.isEmpty()) return null

  return Validation(
    ComposeIdeBundle.message("compose.vector.asset.validation.common.main.duplicate.warning", conflictingSourceSets.joinToString(", ")),
    ValidationSeverity.WARNING
  )
}