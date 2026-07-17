// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset


import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialog.Companion.PREVIEW_SIZE
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.LoadResult
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.LoadedAsset
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Source
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Validation
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.ValidationSeverity
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.ImageIcon
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

internal class VectorAssetLoader(private val renderer: BaseVectorDrawablePreviewRenderer) {

  suspend fun load(source: Source): LoadResult = withContext(Dispatchers.Default) {
    try {
      when (source) {
        is Source.ClipArt -> loadClipArt(source)
        is Source.LocalFile -> loadLocalFile(source)
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LoadResult.Error(e.message ?: "Unknown error")
    }
  }

  private suspend fun loadClipArt(source: Source.ClipArt): LoadResult {
    val xml = withContext(Dispatchers.IO) { source.url.openStream().bufferedReader().readText() }
    val name = (source.name ?: source.url.path.substringAfterLast('/')).substringBeforeLast('.')
    return LoadResult.Success(LoadedAsset(xml, name, renderer.getVectorDrawableSizeDp(xml)))
  }

  private suspend fun loadLocalFile(source: Source.LocalFile): LoadResult {
    val path = Path.of(source.path)
    val warnings = StringBuilder()
    val xml = if (path.extension == "svg") {
      renderer.convertSvgToVectorDrawable(path, warnings) ?: return LoadResult.Error(warnings.toString())
    }
    else {
      withContext(Dispatchers.IO) { path.readText() }
    }
    if (xml.isBlank()) return LoadResult.Error("Empty file")
    return LoadResult.Success(LoadedAsset(xml, path.nameWithoutExtension, renderer.getVectorDrawableSizeDp(xml), warnings.toString()))
  }
}

internal fun ComposeResourcesVectorAssetDialog.createOverrideInfo() = BaseVectorDrawablePreviewRenderer.VectorDrawableOverrideInfo(
  widthProperty.get().toDouble(),
  heightProperty.get().toDouble(),
  if (currentSource is Source.ClipArt) tintColor else null,
  opacityProperty.get() / 100.0,
  mirroringProperty.get()
)

internal fun ComposeResourcesVectorAssetDialog.updatePreview(svgValidation: Validation? = null) {
  val asset = loadedAsset ?: return
  val overrideInfo = createOverrideInfo()

  previewJob?.cancel()
  previewJob = coroutineScope.launch(Dispatchers.Default) {
    try {
      val renderer = BaseVectorDrawablePreviewRenderer.getInstance() ?: return@launch
      val errors = StringBuilder()
      val overriddenXml = renderer.applyOverrides(asset.xml, overrideInfo, errors)
      val result = renderer.renderPreview(overriddenXml, PREVIEW_SIZE, PREVIEW_SIZE)

      withContext(Dispatchers.Main) {
        val errorsStr = errors.toString().trim()
        when (result) {
          is BaseVectorDrawablePreviewRenderer.RenderResult.Success -> {
            finalXmlContent = overriddenXml
            imagePreview.icon = ImageIcon(result.image)
            val xmlValidation = if (errorsStr.isEmpty()) svgValidation else Validation(errorsStr, ValidationSeverity.WARNING)
            updateValidation(xmlValidation)
          }
          is BaseVectorDrawablePreviewRenderer.RenderResult.Error -> {
            val fullErrorMessage = if (errorsStr.isNotEmpty()) "${result.message}\n$errorsStr" else result.message
            clearPreview(Validation(fullErrorMessage, ValidationSeverity.ERROR))
          }
        }
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      clearPreviewOnMain()
    }
  }
}

internal fun ComposeResourcesVectorAssetDialog.applyLoadResult(result: LoadResult, source: Source) {
  when (result) {
    is LoadResult.Success -> applySuccess(result.asset, source)
    is LoadResult.Error -> applyError(result.message)
    is LoadResult.Cancelled -> clearPreview()
  }
}

private fun ComposeResourcesVectorAssetDialog.applySuccess(asset: LoadedAsset, source: Source) {
  val dims = asset.dimensions
  widthProperty.set(dims?.width ?: 0)
  heightProperty.set(dims?.height ?: 0)
  fileNameProperty.set(sanitizeFileName(asset.name))

  val svgValidation = asset.warnings.takeIf { it.isNotEmpty() }?.let { Validation(it, ValidationSeverity.WARNING) }
  if (source is Source.ClipArt) {
    BaseVectorDrawablePreviewRenderer.getInstance()?.let { renderer ->
      val buttonResult = renderer.renderPreview(asset.xml, JBUI.scale(32), JBUI.scale(32))
      if (buttonResult is BaseVectorDrawablePreviewRenderer.RenderResult.Success) {
        val themeAwareImage = renderer.adjustIconColor(source.button, buttonResult.image)
        source.button.icon = ImageIcon(themeAwareImage)
      }
    }
  }
  loadedAsset = asset
  updatePreview(svgValidation)
}

private fun ComposeResourcesVectorAssetDialog.applyError(message: String) {
  clearPreview()
  updateValidation(Validation(message, ValidationSeverity.ERROR))
}