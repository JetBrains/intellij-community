// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset


import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.DrawableDir
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.LoadedAsset
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Page
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Source
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.TreeCellRenderer
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.TreeNode
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Validation
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.ValidationPanel
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.ValidationSeverity
import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.math.roundToInt

internal class ComposeResourcesVectorAssetDialog(
  val project: Project,
  private val targetDir: VirtualFile,
) : DialogWrapper(project) {

  private val propertyGraph = PropertyGraph()

  val localFilePathProperty = propertyGraph.lazyProperty { "" }
  val fileNameProperty = propertyGraph.lazyProperty { "" }
  val widthProperty = propertyGraph.lazyProperty { 0 }
  val heightProperty = propertyGraph.lazyProperty { 0 }

  val opacityProperty = propertyGraph.lazyProperty { 100 }
  val mirroringProperty = propertyGraph.lazyProperty { false }

  val selectedDirProperty = propertyGraph.lazyProperty {
    val targetPath = targetDir.toNioPath()
    drawableDirs.find {
      it.drawablePath.startsWith(targetPath) || targetPath.startsWith(it.resourceDir.directoryPath)
    } ?: drawableDirs.firstOrNull()
  }

  val fileLoadedProperty = propertyGraph.lazyProperty { false }

  var currentSource: Source? = null
  var loadedAsset: LoadedAsset? = null
    set(value) {
      field = value
      fileLoadedProperty.set(value != null)
    }

  var tintColor: Color? = null
  var finalXmlContent: String? = null

  private var loadJob: Job? = null
  var previewJob: Job? = null

  var currentPage = Page.CONFIG

  val outputFileName: String?
    get() = fileNameProperty.get().takeIf { it.isNotBlank() }
            ?: loadedAsset?.name?.let { sanitizeFileName(it) }

  val outputDirectory: Path get() = selectedDirProperty.get()?.drawablePath ?: targetDir.toNioPath()

  val drawableDirs = project.getAllComposeResourcesDirs()
    .filter { it.directoryPath.parent?.exists() == true }
    .map { DrawableDir(it, it.directoryPath.resolve(ResourceType.DRAWABLE.dirName)) }
    .sortedBy { it.resourceDir.sourceSetName }

  val coroutineScope = ComposeResourcesCoroutineScopeService.getInstance(project).childScope("VectorAssetDialog", disposable)
  private val cardLayout = CardLayout()
  private val cardPanel = JPanel(cardLayout)
  val validationPanel = ValidationPanel()

  val imagePreview = JBLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.CENTER
    preferredSize = Dimension(PREVIEW_SIZE, PREVIEW_SIZE)
  }

  val outputPreviewTree: Tree by lazy {
    Tree(DefaultTreeModel(null)).apply {
      isRootVisible = true
      cellRenderer = TreeCellRenderer()
      border = JBUI.Borders.customLine(NamedColorUtil.getBoundsColor())
      rowHeight = JBUI.scale(20)
    }
  }

  private val loader by lazy { BaseVectorDrawablePreviewRenderer.getInstance()?.let { VectorAssetLoader(it) } }
  private var previousButtonRef: JButton? = null
  var clipArtButton: JButton? = null

  init {
    title = ComposeIdeBundle.message("compose.vector.asset.dialog.title")
    setupPropertyListeners()
    init()
    updateValidation()
  }

  private fun setupPropertyListeners() {
    mirroringProperty.afterChange { updatePreview() }
    opacityProperty.afterChange { updatePreview() }
    selectedDirProperty.afterChange { updateOutputPreview() }
    localFilePathProperty.afterChange { onPathChanged() }

    widthProperty.afterChange { value ->
      val aspectRatio = loadedAsset?.aspectRatio ?: return@afterChange
      if (value <= 0) return@afterChange
      val newHeight = (value / aspectRatio).roundToInt()
      if (heightProperty.get() != newHeight) {
        heightProperty.set(newHeight)
      }
      updatePreview()
    }
    heightProperty.afterChange { value ->
      val aspectRatio = loadedAsset?.aspectRatio ?: return@afterChange
      if (value <= 0) return@afterChange
      val newWidth = (value * aspectRatio).roundToInt()
      if (widthProperty.get() != newWidth) {
        widthProperty.set(newWidth)
      }
      updatePreview()
    }
    fileNameProperty.afterChange { updateValidation() }
  }

  override fun createCenterPanel(): JComponent {
    cardPanel.add(createConfigPanel(), "config")
    cardPanel.add(createOutputPanel(), "output")
    setOKButtonText(ComposeIdeBundle.message("compose.vector.asset.button.next.text"))

    return JPanel(BorderLayout()).apply {
      add(cardPanel, BorderLayout.CENTER)
      add(validationPanel, BorderLayout.SOUTH)
      minimumSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
      preferredSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
    }
  }

  override fun createActions(): Array<Action> = arrayOf(
    cancelAction,
    object : DialogWrapperAction(ComposeIdeBundle.message("compose.vector.asset.button.previous.text")) {
      override fun doAction(e: ActionEvent?) {
        showPage(Page.CONFIG)
      }
    },
    okAction
  )

  override fun createButtonsPanel(buttons: MutableList<out JButton>): JPanel {
    val panel = super.createButtonsPanel(buttons)
    buttons.find { it.text == ComposeIdeBundle.message("compose.vector.asset.button.previous.text") }?.let {
      previousButtonRef = it
      it.isEnabled = false
    }
    return panel
  }

  override fun doOKAction() {
    when (currentPage) {
      Page.CONFIG -> {
        validateFirstPage()?.let { validationPanel.showMessage(it); return }
        showPage(Page.OUTPUT)
      }
      Page.OUTPUT -> {
        validateSecondPage()?.let {
          if (it.severity == ValidationSeverity.ERROR) {
            validationPanel.showMessage(it); return
          }
        }
        super.doOKAction()
      }
    }
  }

  override fun isResizable(): Boolean = true

  override fun dispose() {
    loadedAsset = null
    super.dispose()
  }

  fun loadAsset(source: Source) {
    currentSource = source
    loadJob?.cancel()
    loadJob = coroutineScope.launch {
      val result = loader?.load(source) ?: return@launch
      if (currentSource != source) return@launch
      withContext(Dispatchers.Main) {
        applyLoadResult(result, source)
      }
    }
  }

  fun onPathChanged() {
    clearPreview()
    val path = localFilePathProperty.get()
    if (path.isBlank() || !isValidLocalFile(path)) return
    loadAsset(Source.LocalFile(path))
  }

  fun clearPreview(validation: Validation? = null) {
    loadedAsset = null
    currentSource = null
    finalXmlContent = null
    tintColor = null

    fileNameProperty.set("")
    widthProperty.set(0)
    heightProperty.set(0)
    imagePreview.icon = null
    updateValidation(validation)
  }

  suspend fun clearPreviewOnMain() = withContext(Dispatchers.Main) { clearPreview() }

  fun createClipArtButton(picker: BaseVectorIconPicker) = JButton().apply {
    preferredSize = Dimension(JBUI.scale(48), JBUI.scale(48))
    addActionListener {
      val result = picker.pickIcon() ?: return@addActionListener
      loadAsset(Source.ClipArt(result.url, result.name, this))
    }
  }

  private fun showPage(page: Page) {
    currentPage = page
    cardLayout.show(cardPanel, page.name.lowercase())
    previousButtonRef?.isEnabled = page == Page.OUTPUT
    setOKButtonText(
      when (page) {
        Page.CONFIG -> ComposeIdeBundle.message("compose.vector.asset.button.next.text")
        Page.OUTPUT -> ComposeIdeBundle.message("compose.vector.asset.button.finish.text")
      }
    )
    validationPanel.clear()
    if (page == Page.OUTPUT) updateOutputPreview()
    updateValidation()
  }

  private fun updateOutputPreview() {
    val selectedDir = selectedDirProperty.get() ?: return
    if (outputFileName == null) return
    val fileAlreadyExists = selectedDir.drawablePath.resolve("$outputFileName.xml").exists()

    val sourceSetNode = DefaultMutableTreeNode(TreeNode(selectedDir.resourceDir.sourceSetName))
    val resNode = DefaultMutableTreeNode(TreeNode(selectedDir.resourceDir.directoryPath.fileName.toString()))
    val drawableNode = DefaultMutableTreeNode(TreeNode(ResourceType.DRAWABLE.dirName))
    val fileNode = DefaultMutableTreeNode(TreeNode("$outputFileName.xml", isDirectory = false, alreadyExists = fileAlreadyExists))

    drawableNode.add(fileNode)
    resNode.add(drawableNode)
    sourceSetNode.add(resNode)
    outputPreviewTree.model = DefaultTreeModel(sourceSetNode)
    TreeUtil.expandAll(outputPreviewTree)
    updateValidation()
  }

  fun sanitizeFileName(name: String): String = name.lowercase().replace(Regex("[^a-z0-9_]"), "_")

  fun isValidLocalFile(pathString: String): Boolean = try {
    val path = Path.of(pathString)
    path.exists() && path.isRegularFile()
  }
  catch (_: Exception) {
    false
  }

  companion object {
    const val PREVIEW_SIZE = 256
    const val DIALOG_WIDTH = 650
    const val DIALOG_HEIGHT = 350
  }
}