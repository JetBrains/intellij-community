// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset


import com.intellij.compose.ide.plugin.resources.COMPOSE_RESOURCES_DIR
import com.intellij.compose.ide.plugin.resources.ResourceType
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialog.Companion.PREVIEW_SIZE
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.DrawableDir
import com.intellij.compose.ide.plugin.resources.actions.vectorAsset.ComposeResourcesVectorAssetDialogComponents.Source
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.ButtonGroup
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingConstants
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

internal fun ComposeResourcesVectorAssetDialog.createConfigPanel(): JPanel {
  val clipArtRadio = JRadioButton(ComposeIdeBundle.message("compose.vector.asset.type.clip.art.text"), false)
  val localFileRadio = JRadioButton(ComposeIdeBundle.message("compose.vector.asset.type.local.file.text"), true)

  ButtonGroup().apply {
    add(clipArtRadio)
    add(localFileRadio)
  }

  val leftPanel = addLeftPanel(clipArtRadio, localFileRadio)

  val rightPanel = JPanel(BorderLayout()).apply {
    maximumSize = Dimension(PREVIEW_SIZE + JBUI.scale(40), PREVIEW_SIZE + JBUI.scale(40))
    add(JPanel(VerticalLayout(0)).apply {

      add(JPanel(BorderLayout()).apply {
        preferredSize = Dimension(PREVIEW_SIZE, PREVIEW_SIZE)
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
        add(imagePreview, BorderLayout.CENTER)
      })

      add(JBLabel(ComposeIdeBundle.message("compose.vector.asset.preview.label")).apply {
        horizontalAlignment = SwingConstants.CENTER
      })

    }, BorderLayout.NORTH)
  }

  return JPanel(BorderLayout(JBUI.scale(16), 0)).apply {
    add(leftPanel, BorderLayout.CENTER)
    add(rightPanel, BorderLayout.EAST)
  }
}

internal fun ComposeResourcesVectorAssetDialog.addLeftPanel(clipArtRadio: JRadioButton, localFileRadio: JRadioButton): DialogPanel {
  val iconPicker = BaseVectorIconPicker.getInstance()

  lateinit var clipArtRows: RowsRange
  lateinit var localFileRows: RowsRange

  val contentPanel = panel {
    if (iconPicker != null) {
      buttonsGroup {
        row(ComposeIdeBundle.message("compose.vector.asset.type.label")) {
          cell(clipArtRadio).gap(RightGap.COLUMNS)
          cell(localFileRadio)
        }
      }
    }

    row(ComposeIdeBundle.message("compose.vector.asset.file.name.label")) {
      textField()
        .align(AlignX.FILL)
        .comment(ComposeIdeBundle.message("compose.vector.asset.file.name.comment"))
        .bindText(fileNameProperty)
    }

    clipArtRows = rowsRange {
      row(ComposeIdeBundle.message("compose.vector.asset.clip.art.label")) {
        if (iconPicker != null) {
          val button = createClipArtButton(iconPicker)
          clipArtButton = button
          cell(button)
        }
      }

      row(ComposeIdeBundle.message("compose.vector.asset.color.label")) {
        cell(ColorPanel()).applyToComponent {
          selectedColor = JBColor(Color.BLACK, Color.BLACK)
          addActionListener {
            tintColor = selectedColor
            updatePreview()
          }
        }
      }
    }.apply { visible(false) }

    localFileRows = rowsRange {
      row(ComposeIdeBundle.message("compose.vector.asset.path.label")) {
        textFieldWithBrowseButton(
          FileChooserDescriptorFactory.singleFile()
            .withExtensionFilter(ComposeIdeBundle.message("compose.vector.asset.file.types.description"), "xml", "svg"),
          project
        ) { it.path }.align(AlignX.FILL).bindText(localFilePathProperty)
      }
    }

    @Suppress("DialogTitleCapitalization")
    row(ComposeIdeBundle.message("compose.vector.asset.size.label")) {
      intTextField()
        .columns(4)
        .gap(RightGap.SMALL)
        .bindIntText(widthProperty)
        .enabledIf(fileLoadedProperty)
      label(ComposeIdeBundle.message("compose.vector.asset.size.dp.separator.text")).gap(RightGap.SMALL)
      intTextField()
        .columns(4)
        .gap(RightGap.SMALL)
        .bindIntText(heightProperty)
        .enabledIf(fileLoadedProperty)
      label(ComposeIdeBundle.message("compose.vector.asset.size.dp.text"))
    }

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
        .applyToComponent {
          (document as? AbstractDocument)?.documentFilter = object : DocumentFilter() {
            override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
              if (text == null) return super.replace(fb, offset, length, null, attrs)
              val newText = fb.document.getText(0, fb.document.length).replaceRange(offset, offset + length, text)
              if (newText.isEmpty() || newText.toIntOrNull() in 0..100) {
                super.replace(fb, offset, length, text, attrs)
              }
            }
          }
        }
      label("%")
    }

    row {
      checkBox(ComposeIdeBundle.message("compose.vector.asset.mirroring.checkbox.text"))
        .bindSelected(mirroringProperty)
    }
  }

  clipArtRadio.addActionListener {
    clipArtRows.visible(true)
    localFileRows.visible(false)
    val lastClipArtUrl = (currentSource as? Source.ClipArt)?.url
    val lastClipArtName = (currentSource as? Source.ClipArt)?.name
    clipArtButton?.let { button ->
      val url = lastClipArtUrl ?: iconPicker?.getDefaultIconUrl()
      url?.let { loadAsset(Source.ClipArt(it, lastClipArtName, button)) }
    }
  }

  localFileRadio.addActionListener {
    clipArtRows.visible(false)
    localFileRows.visible(true)
    onPathChanged()
  }

  return contentPanel
}

internal fun ComposeResourcesVectorAssetDialog.createOutputPanel(): JPanel {
  val outputPanel = panel {
    row(ComposeIdeBundle.message("compose.vector.asset.source.set.label")) {
      comboBox(drawableDirs, object : ColoredListCellRenderer<DrawableDir>() {
        override fun customizeCellRenderer(
          list: JList<out DrawableDir>,
          value: DrawableDir?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          val dir = value ?: return
          @NlsSafe val sourceSetName = dir.resourceDir.sourceSetName
          @NlsSafe val moduleName = dir.resourceDir.moduleName
          append("$moduleName/${sourceSetName}")
          @NlsSafe val resDirName = dir.resourceDir.directoryPath.fileName?.toString() ?: COMPOSE_RESOURCES_DIR
          @NlsSafe val details = " ($resDirName/${ResourceType.DRAWABLE.dirName})"
          append(details, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES)
        }
      }).align(AlignX.FILL).applyToComponent {
        selectedDirProperty.get()?.let { selectedItem = it }
        addActionListener { selectedDirProperty.set(selectedItem as? DrawableDir) }
      }
    }

    row(ComposeIdeBundle.message("compose.vector.asset.output.directories.label")) {
      cell(JBScrollPane(outputPreviewTree).apply {
        preferredSize = Dimension(450, 150)
      }).align(Align.FILL)
    }
  }
  return JPanel(BorderLayout()).apply {
    add(outputPanel, BorderLayout.WEST)
  }
}