// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.ActionEvent
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Action
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.io.path.exists
import kotlin.math.roundToInt

internal class ComposeResourcesVectorAssetDialog(
  private val project: Project,
  private val targetDir: VirtualFile,
) : DialogWrapper(project) {

  private val propertyGraph = PropertyGraph()
  private val coroutineScope = ComposeResourcesCoroutineScopeService.getInstance(project).childScope("VectorAssetDialog", disposable)

  var finalXmlContent: String? = null
    private set
  val filePath get() = currentFilePath
  val fileName get() = fileNameProperty.get()
  val outputDirectory get() = selectedDirProperty.get()?.drawablePath ?: targetDir.toNioPath()

  private val localFilePathProperty = propertyGraph.lazyProperty { "" }
  private val fileNameProperty = propertyGraph.lazyProperty { "" }
  private val widthProperty = propertyGraph.lazyProperty { 0 }
  private val heightProperty = propertyGraph.lazyProperty { 0 }
  private val opacityProperty = propertyGraph.lazyProperty { 100 }
  private val mirroringProperty = propertyGraph.lazyProperty { false }
  private val fileLoadedProperty = propertyGraph.lazyProperty { false }
  private val isClipArtModeProperty = propertyGraph.lazyProperty { false }
  private val widthHasFocus = propertyGraph.property(false)
  private val heightHasFocus = propertyGraph.property(false)

  private val selectedDirProperty = propertyGraph.lazyProperty {
    val targetPath = targetDir.toNioPath()
    drawableDirs.find {
      it.drawablePath.startsWith(targetPath) || targetPath.startsWith(it.resourceDir.directoryPath)
    } ?: drawableDirs.firstOrNull()
  }

  private val drawableDirs = project.getAllComposeResourcesDirs()
    .filter { resDir -> resDir.directoryPath.parent?.exists() == true }
    .map { resDir ->
      ComposeResourcesDrawableDir(resDir, resDir.directoryPath.resolve("drawable"))
    }.sortedBy { it.resourceDir.sourceSetName }

  private var currentFilePath = ""
  private var currentFileJob: Job? = null
  private var currentTempFile: Path? = null
  private var previewJob: Job? = null
  private var xmlContent: String? = null
  private var aspectRatio = 1.0
  private var tintColor: Color? = null
  private var isSecondPage = false
  private var lastClipArtUrl: URL? = null
  private var lastClipArtName: String? = null
  private var conversionWarningText = ""
  private var previewValidation: ValidationInfo? = null

  private val cardLayout = CardLayout()
  private val cardPanel = JPanel(cardLayout)
  private val validationPanel = ComposeResourcesValidationPanel()
  private val imagePreview = JBLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.CENTER
    preferredSize = Dimension(PREVIEW_SIZE, PREVIEW_SIZE)
  }

  private val outputPreviewTree: Tree by lazy {
    Tree(DefaultTreeModel(null)).apply {
      isRootVisible = true
      cellRenderer = ComposeResourcesOutputTreeCellRenderer()
      border = JBUI.Borders.customLine(NamedColorUtil.getBoundsColor())
      rowHeight = JBUI.scale(20)
    }
  }
  private val outputPanel: DialogPanel by lazy { createOutputDialogPanel() }

  private var previousButtonRef: JButton? = null
  private var clipArtButton: JButton? = null

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
      if (value <= 0 || !widthHasFocus.get()) return@afterChange
      heightProperty.set((value / aspectRatio).roundToInt())
      updateValidation()
    }
    heightProperty.afterChange { value ->
      if (value <= 0 || !heightHasFocus.get()) return@afterChange
      widthProperty.set((value * aspectRatio).roundToInt())
      updateValidation()
    }
  }

  override fun createCenterPanel(): JComponent {
    cardPanel.add(createAssetConfigPanel(), "config")
    cardPanel.add(createOutputPanel(), "output")
    setOKButtonText(ComposeIdeBundle.message("compose.vector.asset.button.next.text"))

    return JPanel(BorderLayout()).apply {
      add(cardPanel, BorderLayout.CENTER)
      add(validationPanel, BorderLayout.SOUTH)
      minimumSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
      preferredSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
    }
  }

  override fun dispose() {
    currentTempFile?.let {
      try {
        Files.deleteIfExists(it)
      }
      catch (_: Exception) {
      }
    }
    super.dispose()
  }

  private fun createAssetConfigPanel(): JPanel {
    val iconPicker = BaseVectorIconPicker.getInstance()

    lateinit var clipArtRows: RowsRange
    lateinit var localFileRows: RowsRange

    val clipArtRadio = JRadioButton(ComposeIdeBundle.message("compose.vector.asset.type.clip.art.text"), false)
    val localFileRadio = JRadioButton(ComposeIdeBundle.message("compose.vector.asset.type.local.file.text"), true)
    ButtonGroup().apply {
      add(clipArtRadio)
      add(localFileRadio)
    }

    val leftPanel = panel {
      if (iconPicker != null) addAssetTypeRow(clipArtRadio, localFileRadio)

      addFileNameRow()

      clipArtRows = rowsRange {
        addClipArtRow(iconPicker)
        addColorRow()
      }.apply { visible(false) }

      localFileRows = rowsRange {
        addPathRow()
      }

      addSizeRow()
      addOpacityRow()
      addMirroringRow()
    }

    clipArtRadio.addActionListener {
      isClipArtModeProperty.set(true)
      clipArtRows.visible(true)
      localFileRows.visible(false)
      clipArtButton?.let { button ->
        val url = lastClipArtUrl ?: iconPicker?.getDefaultIconUrl()
        url?.let { loadClipArtIcon(button, it, lastClipArtName) }
      }
    }
    localFileRadio.addActionListener {
      isClipArtModeProperty.set(false)
      clipArtRows.visible(false)
      localFileRows.visible(true)

      onPathChanged()
    }

    val previewPanel = JPanel(BorderLayout()).apply {
      preferredSize = Dimension(PREVIEW_SIZE, PREVIEW_SIZE)
      border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
      add(imagePreview, BorderLayout.CENTER)
    }

    val rightPanel = JPanel(BorderLayout()).apply {
      val width = PREVIEW_SIZE + JBUI.scale(40)
      maximumSize = Dimension(width, width)

      val previewWithLabel = JPanel(VerticalLayout(0)).apply {
        add(previewPanel)
        add(JBLabel(ComposeIdeBundle.message("compose.vector.asset.preview.label")).apply {
          horizontalAlignment = SwingConstants.CENTER
        })
      }
      add(previewWithLabel, BorderLayout.NORTH)
    }

    return JPanel(BorderLayout(JBUI.scale(16), 0)).apply {
      add(leftPanel, BorderLayout.CENTER)
      add(rightPanel, BorderLayout.EAST)
    }
  }

  private fun Panel.addAssetTypeRow(clipArtRadio: JRadioButton, localFileRadio: JRadioButton) {
    buttonsGroup {
      row(ComposeIdeBundle.message("compose.vector.asset.type.label")) {
        cell(clipArtRadio).gap(RightGap.COLUMNS)
        cell(localFileRadio)
      }
    }
  }

  private fun Panel.addFileNameRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.file.name.label")) {
      textField()
        .align(AlignX.FILL)
        .comment(ComposeIdeBundle.message("compose.vector.asset.file.name.comment"))
        .bindText(fileNameProperty)
    }
  }

  private fun Panel.addClipArtRow(iconPicker: BaseVectorIconPicker?) {
    row(ComposeIdeBundle.message("compose.vector.asset.clip.art.label")) {
      if (iconPicker != null) {
        val button = createClipArtButton(iconPicker)
        clipArtButton = button
        cell(button)
      }
    }
  }

  private fun Panel.addColorRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.color.label")) {
      cell(ColorPanel()).applyToComponent {
        selectedColor = JBColor(Color.BLACK, Color.BLACK)
        addActionListener {
          tintColor = selectedColor
          updatePreview()
        }
      }
    }
  }

  private fun Panel.addPathRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.path.label")) {
      textFieldWithBrowseButton(
        FileChooserDescriptorFactory.singleFile()
          .withExtensionFilter(ComposeIdeBundle.message("compose.vector.asset.file.types.description"), "xml", "svg"),
        project
      ) { file ->
        file.path
      }.align(AlignX.FILL)
        .bindText(localFilePathProperty)
    }
  }

  private fun Panel.addSizeRow() {
    @Suppress("DialogTitleCapitalization")
    row(ComposeIdeBundle.message("compose.vector.asset.size.label")) {
      intTextField()
        .columns(4)
        .gap(RightGap.SMALL)
        .bindIntText(widthProperty)
        .enabledIf(fileLoadedProperty)
        .applyToComponent { setupFocusListener(widthHasFocus) }

      label(ComposeIdeBundle.message("compose.vector.asset.size.dp.separator.text")).gap(RightGap.SMALL)

      intTextField()
        .columns(4)
        .gap(RightGap.SMALL)
        .bindIntText(heightProperty)
        .enabledIf(fileLoadedProperty)
        .applyToComponent { setupFocusListener(heightHasFocus) }

      label(ComposeIdeBundle.message("compose.vector.asset.size.dp.text"))
    }
  }

  private fun JTextField.setupFocusListener(focusProperty: GraphProperty<Boolean>) =
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        focusProperty.set(true)
      }

      override fun focusLost(e: FocusEvent?) {
        focusProperty.set(false)
      }
    })

  private fun Panel.addOpacityRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.opacity.label")) {
      slider(0, 100, 0, 0)
        .align(AlignX.FILL)
        .applyToComponent {
          value = 100
          addChangeListener { opacityProperty.set(value) }
          opacityProperty.afterChange { if (value != it) value = it }
        }
      intTextField(0..100)
        .columns(3)
        .gap(RightGap.SMALL)
        .bindIntText(opacityProperty)
      label("%")
    }
  }

  private fun Panel.addMirroringRow() {
    row {
      checkBox(ComposeIdeBundle.message("compose.vector.asset.mirroring.checkbox.text"))
        .bindSelected(mirroringProperty)
    }
  }

  private fun createOutputPanel(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(outputPanel, BorderLayout.WEST)
    }
  }

  private fun createOutputDialogPanel(): DialogPanel = panel {
    addResDirectoryRow()
    addOutputDirectoriesRow()
  }

  private fun Panel.addResDirectoryRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.res.directory.label")) {
      comboBox(drawableDirs, object : ColoredListCellRenderer<ComposeResourcesDrawableDir>() {
        override fun customizeCellRenderer(
          list: JList<out ComposeResourcesDrawableDir>,
          value: ComposeResourcesDrawableDir?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          val dir = value ?: return
          @NlsSafe val sourceSetName = dir.resourceDir.sourceSetName
          @NlsSafe val resDirName = dir.resourceDir.directoryPath.fileName?.toString() ?: "composeResources"

          append(sourceSetName)
          append(" ($resDirName/${ResourceType.DRAWABLE.dirName})", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
        }
      }).align(AlignX.FILL).applyToComponent {
        selectedDirProperty.get()?.let { selectedItem = it }
        addActionListener { selectedDirProperty.set(selectedItem as? ComposeResourcesDrawableDir) }
      }
    }
  }

  private fun Panel.addOutputDirectoriesRow() {
    row(ComposeIdeBundle.message("compose.vector.asset.output.directories.label")) {
      cell(JBScrollPane(outputPreviewTree).apply {
        preferredSize = Dimension(450, 150)
      }).align(Align.FILL)
    }
  }

  private fun createClipArtButton(picker: BaseVectorIconPicker) = JButton().apply {
    preferredSize = Dimension(JBUI.scale(48), JBUI.scale(48))

    addActionListener {
      val result = picker.pickIcon() ?: return@addActionListener
      loadClipArtIcon(this, result.url, result.name)
    }
  }

  override fun createActions(): Array<Action> = arrayOf(
    cancelAction,
    object : DialogWrapperAction(ComposeIdeBundle.message("compose.vector.asset.button.previous.text")) {
      override fun doAction(e: ActionEvent?) {
        isSecondPage = false
        cardLayout.show(cardPanel, "config")
        setOKButtonText(ComposeIdeBundle.message("compose.vector.asset.button.next.text"))
        previousButtonRef?.isEnabled = false
        validationPanel.clear()
        updateValidation()
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
    if (isSecondPage) {
      validateSecondPage()?.let { info ->
        if (info.severity == ComposeResourcesSeverity.ERROR) {
          validationPanel.showMessage(info)
          return
        }
      }
      super.doOKAction()
      return
    }

    validateFirstPage(strict = true)?.let {
      validationPanel.showMessage(it)
      return
    }
    isSecondPage = true
    cardLayout.show(cardPanel, "output")
    setOKButtonText(ComposeIdeBundle.message("compose.vector.asset.button.finish.text"))
    previousButtonRef?.isEnabled = true
    validationPanel.clear()
    updateOutputPreview()
  }

  override fun isResizable(): Boolean = true

  private fun updateValidation() {
    val formError = if (isSecondPage) validateSecondPage() else validateFirstPage(strict = false)

    if (formError != null) {
      validationPanel.showMessage(formError)
      isOKActionEnabled = formError.severity == ComposeResourcesSeverity.WARNING
    }
    else if (!isSecondPage && previewValidation != null) {
      validationPanel.showMessage(previewValidation!!)
      isOKActionEnabled = previewValidation!!.severity == ComposeResourcesSeverity.WARNING && fileLoadedProperty.get()
    }
    else if (!isSecondPage && conversionWarningText.isNotEmpty()) {
      validationPanel.showMessage(ValidationInfo(conversionWarningText, ComposeResourcesSeverity.WARNING))
      isOKActionEnabled = fileLoadedProperty.get()
    }
    else {
      validationPanel.clear()
      isOKActionEnabled = isSecondPage || fileLoadedProperty.get()
    }
  }

  private fun validateFirstPage(strict: Boolean): ValidationInfo? {
    if (!isClipArtModeProperty.get() && currentFilePath.isNotBlank() && !isValidLocalFile(currentFilePath)) {
      return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.path.not.exist.error"),
        ComposeResourcesSeverity.ERROR
      )
    }

    if (fileLoadedProperty.get()) {
      if (widthProperty.get() <= 0) return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.width.positive.error"),
        ComposeResourcesSeverity.ERROR
      )
      if (heightProperty.get() <= 0) return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.height.positive.error"),
        ComposeResourcesSeverity.ERROR
      )
    }

    if (strict && (currentFilePath.isBlank() || xmlContent.isNullOrBlank())) {
      return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.no.file.error"),
        ComposeResourcesSeverity.ERROR
      )
    }

    if (strict && getOutputFileName().isBlank()) {
      return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.empty.name.error"),
        ComposeResourcesSeverity.ERROR
      )
    }

    return null
  }

  private fun validateSecondPage(): ValidationInfo? {
    if (selectedDirProperty.get() == null) {
      return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.no.output.directory.error"),
        ComposeResourcesSeverity.ERROR
      )
    }

    val selectedDir = selectedDirProperty.get() ?: return null
    val isExisting = selectedDir.drawablePath.resolve("${getOutputFileName()}.xml").exists()

    if (isExisting) {
      return ValidationInfo(
        ComposeIdeBundle.message("compose.vector.asset.validation.file.exists.warning", getOutputFileName()),
        ComposeResourcesSeverity.WARNING
      )
    }
    return null
  }

  private fun onPathChanged() {
    currentFileJob?.cancel()
    val path = localFilePathProperty.get()
    currentFilePath = path
    clearPreview()
    updateValidation()

    if (path.isBlank() || !isValidLocalFile(path)) return

    processAndLoadLocalFile()
  }

  private fun updateOutputPreview() {
    val selectedDir = selectedDirProperty.get() ?: return

    val outputFileName = getOutputFileName()
    val fileAlreadyExists = selectedDir.drawablePath.resolve("$outputFileName.xml").exists()

    val resPath = selectedDir.resourceDir.directoryPath
    val sourceSetNode = DefaultMutableTreeNode(ComposeResourcesTreeNodeInfo(selectedDir.resourceDir.sourceSetName, isDirectory = true))
    val resNode = DefaultMutableTreeNode(ComposeResourcesTreeNodeInfo(resPath.fileName?.toString() ?: "res", isDirectory = true))
    val drawableNode = DefaultMutableTreeNode(ComposeResourcesTreeNodeInfo("drawable", isDirectory = true))
    val fileNode =
      DefaultMutableTreeNode(ComposeResourcesTreeNodeInfo("$outputFileName.xml", isDirectory = false, alreadyExists = fileAlreadyExists))

    drawableNode.add(fileNode)
    resNode.add(drawableNode)
    sourceSetNode.add(resNode)

    outputPreviewTree.model = DefaultTreeModel(sourceSetNode)
    TreeUtil.expandAll(outputPreviewTree)
    updateValidation()
  }

  fun getOutputFileName(): String {
    val name = fileNameProperty.get()
    if (name.isNotBlank()) return sanitizeFileName(name)

    if (isClipArtModeProperty.get()) {
      val urlString = lastClipArtUrl?.toString() ?: return "vector"
      val rawName = lastClipArtName ?: urlString.substringAfterLast('/').substringBeforeLast('.')
      return sanitizeFileName(rawName)
    }

    if (currentFilePath.isBlank()) return "vector"

    return try {
      val rawName = Path.of(currentFilePath).fileName.toString().substringBeforeLast('.')
      sanitizeFileName(rawName)
    }
    catch (_: Exception) {
      "vector"
    }
  }

  private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
  }

  private fun loadClipArtIcon(button: JButton, url: URL, iconName: String?) {
    lastClipArtUrl = url
    lastClipArtName = iconName
    currentFileJob?.cancel()

    currentFileJob = coroutineScope.launch(Dispatchers.Default) {
      try {
        val xml = withContext(Dispatchers.IO) {
          url.openStream().bufferedReader().readText()
        }
        ensureActive()

        val renderer = BaseVectorDrawablePreviewRenderer.getInstance() ?: return@launch

        val dimensions = renderer.getVectorDrawableSizeDp(xml)
        val newAspectRatio = dimensions?.let {
          if (it.height > 0) it.width.toDouble() / it.height else 1.0
        } ?: 1.0

        withContext(Dispatchers.Main) {
          if (lastClipArtUrl != url) return@withContext

          xmlContent = xml
          aspectRatio = newAspectRatio
          conversionWarningText = ""

          if (dimensions != null) {
            widthProperty.set(dimensions.width)
            heightProperty.set(dimensions.height)
          }
          val name = (iconName ?: url.toString().substringAfterLast('/')).substringBeforeLast('.')
          fileNameProperty.set(name)
          fileLoadedProperty.set(true)

          updateValidation()
          updatePreview()
        }

        ensureActive()

        currentTempFile?.let {
          withContext(Dispatchers.IO) {
            Files.deleteIfExists(it)
          }
        }

        val tempFile = withContext(Dispatchers.IO) {
          Files.createTempFile("clipart_", ".xml")
        }
        currentTempFile = tempFile

        withContext(Dispatchers.IO) {
          Files.writeString(tempFile, xml)
        }

        withContext(Dispatchers.Main) {
          if (lastClipArtUrl != url) return@withContext
          currentFilePath = tempFile.toAbsolutePath().toString()
        }
        val buttonResult = renderer.renderPreview(xml, JBUI.scale(32), JBUI.scale(32))

        if (buttonResult is BaseVectorDrawablePreviewRenderer.RenderResult.Success) {
          withContext(Dispatchers.Main) {
            if (lastClipArtUrl != url) return@withContext
            val themeAwareImage = renderer.adjustIconColor(button, buttonResult.image)
            button.icon = ImageIcon(themeAwareImage)
          }
        }
      }
      catch (e: Exception) {
        if (Logger.shouldRethrow(e)) throw e
        clearPreviewOnMain()
      }
    }
  }

  private fun processAndLoadLocalFile() {
    currentFileJob?.cancel()

    val pathToLoad = currentFilePath
    if (pathToLoad.isBlank()) return

    currentFileJob = coroutineScope.launch(Dispatchers.Default) {
      val renderer = BaseVectorDrawablePreviewRenderer.getInstance() ?: run {
        clearPreviewOnMain()
        return@launch
      }
      val conversionWarnings = StringBuilder()

      val xml = try {
        val path = Path.of(pathToLoad)
        if (pathToLoad.endsWith(".svg")) {
          val resultXml = renderer.convertSvgToVectorDrawable(path, conversionWarnings)
          if (resultXml.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
              clearPreview()
              previewValidation = ValidationInfo(conversionWarnings.toString(), ComposeResourcesSeverity.ERROR)
              updateValidation()
            }
            return@launch
          }
          resultXml
        }
        else {
          withContext(Dispatchers.IO) { Files.readString(path) }
        }
      }
      catch (e: Exception) {
        if (Logger.shouldRethrow(e)) throw e
        clearPreviewOnMain()
        return@launch
      }

      if (xml.isBlank()) {
        clearPreviewOnMain()
        return@launch
      }

      ensureActive()
      val dimensions = renderer.getVectorDrawableSizeDp(xml)
      val newAspectRatio = dimensions?.let {
        if (it.height > 0) it.width.toDouble() / it.height else 1.0
      } ?: 1.0

      withContext(Dispatchers.Main) {
        if (currentFilePath != pathToLoad) return@withContext

        conversionWarningText = conversionWarnings.toString()

        xmlContent = xml
        aspectRatio = newAspectRatio
        fileLoadedProperty.set(true)

        widthProperty.set(dimensions?.width ?: 0)
        heightProperty.set(dimensions?.height ?: 0)

        val fileName = Path.of(pathToLoad).fileName.toString().substringBeforeLast('.')
        fileNameProperty.set(fileName)

        updateValidation()
        updatePreview()
      }
    }
  }

  private fun updatePreview() {
    val currentXml = xmlContent ?: return
    val overrideInfo = createOverrideInfo()

    previewJob?.cancel()
    previewJob = coroutineScope.launch(Dispatchers.Default) {
      try {
        val renderer = BaseVectorDrawablePreviewRenderer.getInstance() ?: return@launch
        val errors = StringBuilder()
        val overriddenXml = renderer.applyOverrides(currentXml, overrideInfo, errors)
        ensureActive()
        val result = renderer.renderPreview(overriddenXml, PREVIEW_SIZE, PREVIEW_SIZE)

        withContext(Dispatchers.Main) {
          finalXmlContent = overriddenXml

          val errorsStr = errors.toString().trim()

          previewValidation = when (result) {
            is BaseVectorDrawablePreviewRenderer.RenderResult.Success -> {
              imagePreview.icon = ImageIcon(result.image)
              if (errorsStr.isEmpty()) null
              else {
                ValidationInfo(errorsStr, ComposeResourcesSeverity.WARNING)
              }
            }
            is BaseVectorDrawablePreviewRenderer.RenderResult.Error -> {
              imagePreview.icon = null
              val fullErrorMessage = if (errorsStr.isNotEmpty()) "${result.message}\n$errorsStr" else result.message
              ValidationInfo(fullErrorMessage, ComposeResourcesSeverity.ERROR)
            }
          }
          updateValidation()
        }
      }
      catch (e: Exception) {
        if (Logger.shouldRethrow(e)) throw e
        clearPreviewOnMain()
      }
    }
  }

  private fun createOverrideInfo() = BaseVectorDrawablePreviewRenderer.VectorDrawableOverrideInfo(
    widthProperty.get().toDouble(),
    heightProperty.get().toDouble(),
    if (isClipArtModeProperty.get()) tintColor else null,
    opacityProperty.get() / 100.0,
    mirroringProperty.get()
  )

  private fun isValidLocalFile(pathString: String): Boolean {
    return try {
      val path = Path.of(pathString)
      path.exists() && Files.isRegularFile(path)
    }
    catch (_: Exception) {
      false
    }
  }

  private fun clearPreview() {
    xmlContent = null
    finalXmlContent = null
    aspectRatio = 1.0
    conversionWarningText = ""
    previewValidation = null
    tintColor = null

    fileLoadedProperty.set(false)
    fileNameProperty.set("")
    widthProperty.set(0)
    heightProperty.set(0)
    imagePreview.icon = null
    updateValidation()
  }

  private suspend fun clearPreviewOnMain() {
    withContext(Dispatchers.Main) {
      clearPreview()
    }
  }

  companion object {
    private const val PREVIEW_SIZE = 256
    private const val DIALOG_WIDTH = 650
    private const val DIALOG_HEIGHT = 300
  }
}