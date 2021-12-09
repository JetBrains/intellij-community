// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.ui

import com.intellij.copyright.CopyrightBundle
import com.intellij.copyright.CopyrightManager.Companion.getInstance
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.maddyhome.idea.copyright.CopyrightUpdaters
import com.maddyhome.idea.copyright.DEFAULT_COPYRIGHT_NOTICE
import com.maddyhome.idea.copyright.options.LanguageOptions
import com.maddyhome.idea.copyright.options.Options
import com.maddyhome.idea.copyright.pattern.EntityUtil
import com.maddyhome.idea.copyright.pattern.VelocityHelper
import com.maddyhome.idea.copyright.util.FileTypeUtil
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.EventListenerList
import kotlin.math.max
import kotlin.math.min

class TemplateCommentPanel(_fileType: FileType?,
                           private val parentPanel: TemplateCommentPanel?,
                           private val project: Project,
                           vararg locations: @NlsContexts.RadioButton String) : SearchableConfigurable {

  val fileType: FileType
  private val allowBlock: Boolean
  private val listeners = EventListenerList()
  private val fileLocations = mutableListOf<JRadioButton>()

  private lateinit var noCopyright: JBRadioButton
  private lateinit var useDefaultSettingsRadioButton: JBRadioButton
  private lateinit var useCustomFormattingOptionsRadioButton: JBRadioButton

  private lateinit var commentTypeGroup: Row
  private lateinit var blockComment: JBRadioButton
  private lateinit var prefixLines: JBCheckBox
  private lateinit var lineComment: JBRadioButton

  private lateinit var relativeLocationGroup: Row
  private lateinit var before: JBRadioButton
  private lateinit var after: JBRadioButton

  private lateinit var borderGroup: Row
  private lateinit var separatorBefore: JBCheckBox
  private lateinit var lengthBefore: JBTextField
  private lateinit var separatorAfter: JBCheckBox
  private lateinit var lengthAfter: JBTextField
  private lateinit var filler: JBTextField

  private lateinit var box: JBCheckBox
  private lateinit var addBlankBefore: JBCheckBox
  private lateinit var addBlank: JBCheckBox

  private lateinit var preview: JTextArea

  val panel = panel {
    val updateOverrideListener = ActionListener { updateOverride() }
    val changeEventListener = ActionListener { fireChangeEvent() }
    val updateBoxListener = ActionListener {
      fireChangeEvent()
      updateBox()
    }
    val documentAdapter = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        fireChangeEvent()
        updateBox()
      }
    }


    buttonsGroup {
      row {
        noCopyright = radioButton(CopyrightBundle.message("settings.copyright.formatting.no.copyright"))
          .applyToComponent { addActionListener(updateOverrideListener) }
          .component
      }
      row {
        useDefaultSettingsRadioButton = radioButton(CopyrightBundle.message("settings.copyright.formatting.use.default.settings"))
          .applyToComponent { addActionListener(updateOverrideListener) }
          .component
      }
      row {
        useCustomFormattingOptionsRadioButton = radioButton(
          CopyrightBundle.message("settings.copyright.formatting.use.custom.formatting.options"))
          .applyToComponent { addActionListener(updateOverrideListener) }
          .component
      }
    }

    row {
      panel {
        commentTypeGroup = group(CopyrightBundle.message("settings.copyright.formatting.comment.type")) {
          buttonsGroup {
            row {
              blockComment = radioButton(CopyrightBundle.message("settings.copyright.formatting.use.block.comment"))
                .applyToComponent { addActionListener(changeEventListener) }
                .component
            }
            indent {
              row {
                prefixLines = checkBox(CopyrightBundle.message("settings.copyright.formatting.prefix.each.line"))
                  .applyToComponent { addActionListener(changeEventListener) }
                  .enabledIf(blockComment.selected)
                  .component
              }
            }
            row {
              lineComment = radioButton(CopyrightBundle.message("settings.copyright.formatting.use.line.comment"))
                .applyToComponent { addActionListener(changeEventListener) }
                .component
            }
          }
        }
        relativeLocationGroup = group(CopyrightBundle.message("settings.copyright.formatting.relative.location")) {
          buttonsGroup {
            row {
              before = radioButton(CopyrightBundle.message("settings.copyright.formatting.before.other.comments"))
                .component
            }
            row {
              after = radioButton(CopyrightBundle.message("settings.copyright.formatting.after.other.comments"))
                .component
            }
          }
        }
      }.resizableColumn()
        .verticalAlign(VerticalAlign.TOP)
        .gap(RightGap.COLUMNS)

      panel {
        borderGroup = group(CopyrightBundle.message("settings.copyright.formatting.borders")) {
          row {
            separatorBefore = checkBox(CopyrightBundle.message("settings.copyright.formatting.separator.before"))
              .applyToComponent { addActionListener(updateBoxListener) }
              .component
            lengthBefore = intTextField()
              .label(CopyrightBundle.message("settings.copyright.formatting.length"))
              .applyToComponent { document.addDocumentListener(documentAdapter) }
              .enabledIf(separatorBefore.selected)
              .component
          }.layout(RowLayout.PARENT_GRID)
          row {
            separatorAfter = checkBox(CopyrightBundle.message("settings.copyright.formatting.separator.after"))
              .applyToComponent { addActionListener(updateBoxListener) }
              .component
            lengthAfter = intTextField()
              .label(CopyrightBundle.message("settings.copyright.formatting.length"))
              .applyToComponent { document.addDocumentListener(documentAdapter) }
              .enabledIf(separatorAfter.selected)
              .component
          }.layout(RowLayout.PARENT_GRID)
          row {
            filler = textField()
              .label(CopyrightBundle.message("settings.copyright.formatting.separator"))
              .applyToComponent { document.addDocumentListener(documentAdapter) }
              .columns(4)
              .component
          }
          row {
            box = checkBox(CopyrightBundle.message("settings.copyright.formatting.box"))
              .applyToComponent { addActionListener(changeEventListener) }
              .component
          }
          row {
            addBlankBefore = checkBox(CopyrightBundle.message("settings.copyright.formatting.add.blank.line.before"))
              .component
          }
          row {
            addBlank = checkBox(CopyrightBundle.message("settings.copyright.formatting.add.blank.line.after"))
              .component
          }
        }
      }.resizableColumn()
        .verticalAlign(VerticalAlign.TOP)
    }

    if (locations.isNotEmpty()) {
      group(CopyrightBundle.message("settings.copyright.formatting.location.in.file")) {
        buttonsGroup {
          for (location in locations) {
            row {
              fileLocations += radioButton(location).component
            }
          }
        }
      }
    }

    row {
      preview = textArea()
        .horizontalAlign(HorizontalAlign.FILL)
        .verticalAlign(VerticalAlign.FILL)
        .applyToComponent {
          isEditable = false
          font = EditorFontType.getGlobalPlainFont()
        }
        .component
    }.resizableRow()
  }

  init {
    if (_fileType == null) {
      useDefaultSettingsRadioButton.isVisible = false
      useCustomFormattingOptionsRadioButton.isVisible = false
      noCopyright.isVisible = false
    }

    fileType = _fileType ?: StdFileTypes.JAVA
    allowBlock = FileTypeUtil.hasBlockComment(fileType)

    parentPanel?.addOptionChangeListener(TemplateOptionsPanelListener { updateOverride() })

    addOptionChangeListener { showPreview(getOptions()) }
  }

  override fun getDisplayName(): String {
    return if (fileType is LanguageFileType) fileType.language.displayName else fileType.displayName
  }

  override fun getHelpTopic(): String {
    return "copyright.filetypes"
  }

  override fun getOriginalClass(): Class<*> {
    val provider = CopyrightUpdaters.INSTANCE.forFileType(fileType)
    return provider?.javaClass ?: super.getOriginalClass()
  }

  override fun getId(): String {
    return helpTopic + "." + fileType.name
  }

  override fun createComponent(): JComponent {
    return panel
  }

  override fun isModified(): Boolean {
    if (parentPanel == null) {
      return getCopyrightOptions().templateOptions != getOptions()
    }

    return getCopyrightOptions().getOptions(fileType.name) != getOptions()
  }

  override fun apply() {
    val options = getCopyrightOptions()
    if (parentPanel == null) {
      options.templateOptions = getOptions()
    }
    else {
      options.setOptions(fileType.name, getOptions())
    }
  }

  override fun reset() {
    val options = if (parentPanel == null) getCopyrightOptions().templateOptions
    else getCopyrightOptions().getOptions(fileType.name)
    val isBlock = options.isBlock
    if (isBlock) {
      blockComment.isSelected = true
    }
    else {
      lineComment.isSelected = true
    }

    prefixLines.isSelected = !allowBlock || options.isPrefixLines
    separatorAfter.isSelected = options.isSeparateAfter
    separatorBefore.isSelected = options.isSeparateBefore
    lengthBefore.text = options.getLenBefore().toString()
    lengthAfter.text = options.getLenAfter().toString()
    filler.text = if (options.getFiller() === LanguageOptions.DEFAULT_FILLER) "" else options.getFiller()
    box.isSelected = options.isBox

    val fileTypeOverride = options.getFileTypeOverride()
    useDefaultSettingsRadioButton.isSelected = fileTypeOverride == LanguageOptions.USE_TEMPLATE
    useCustomFormattingOptionsRadioButton.isSelected = fileTypeOverride == LanguageOptions.USE_TEXT
    noCopyright.isSelected = fileTypeOverride == LanguageOptions.NO_COPYRIGHT
    if (options.isRelativeBefore) {
      before.isSelected = true
    }
    else {
      after.isSelected = true
    }
    addBlank.isSelected = options.isAddBlankAfter
    addBlankBefore.isSelected = options.isAddBlankBefore

    if (fileLocations.isNotEmpty()) {
      var choice = options.getFileLocation() - 1
      choice = max(0, min(choice, fileLocations.size - 1))
      fileLocations[choice].isSelected = true
    }

    updateOverride()
  }

  private fun getOptions(): LanguageOptions {
    // If this is a fully custom comment we should really ensure there are no blank lines in the comments outside
    // of a block comment. If there are any blank lines the replacement logic will fall apart.
    val result = LanguageOptions()
    result.isBlock = blockComment.isSelected
    result.isPrefixLines = !allowBlock || prefixLines.isSelected
    result.isSeparateAfter = separatorAfter.isSelected
    result.isSeparateBefore = separatorBefore.isSelected
    try {
      result.setLenBefore(lengthBefore.text.toInt())
      result.setLenAfter(lengthAfter.text.toInt())
    }
    catch (e: NumberFormatException) {
      //leave blank
    }
    result.isBox = box.isSelected
    val filler = filler.text
    if (filler.isNotEmpty()) {
      result.setFiller(filler)
    }
    else {
      result.setFiller(LanguageOptions.DEFAULT_FILLER)
    }
    result.setFileTypeOverride(getOverrideChoice())
    result.isRelativeBefore = before.isSelected
    result.isAddBlankAfter = addBlank.isSelected
    result.isAddBlankBefore = addBlankBefore.isSelected
    if (fileLocations.isNotEmpty()) {
      for (i in fileLocations.indices) {
        if (fileLocations[i].isSelected) {
          result.setFileLocation(i + 1)
        }
      }
    }
    return result
  }

  private fun getOverrideChoice(): Int {
    return if (useDefaultSettingsRadioButton.isSelected) LanguageOptions.USE_TEMPLATE
    else
      if (noCopyright.isSelected) LanguageOptions.NO_COPYRIGHT else LanguageOptions.USE_TEXT
  }

  private fun updateOverride() {
    val choice = getOverrideChoice()
    val parentOpts = parentPanel?.getOptions()
    when (choice) {
      LanguageOptions.NO_COPYRIGHT -> {
        enableFormattingOptions(false)
        showPreview(getOptions())
        relativeLocationGroup.enabled(false)
        before.isEnabled = false
        after.isEnabled = false
        addBlank.isEnabled = false
        addBlankBefore.isEnabled = false
        if (fileLocations.isNotEmpty()) {
          for (fileLocation in fileLocations) {
            fileLocation.isEnabled = false
          }
        }
      }
      LanguageOptions.USE_TEMPLATE -> {
        val isTemplate = parentPanel == null
        enableFormattingOptions(isTemplate)
        showPreview(parentOpts ?: getOptions())
        relativeLocationGroup.enabled(isTemplate)
        before.isEnabled = isTemplate
        after.isEnabled = isTemplate
        addBlank.isEnabled = isTemplate
        addBlankBefore.isEnabled = isTemplate
        if (fileLocations.isNotEmpty()) {
          for (fileLocation in fileLocations) {
            fileLocation.isEnabled = true
          }
        }
      }
      LanguageOptions.USE_TEXT -> {
        enableFormattingOptions(true)
        showPreview(getOptions())
        relativeLocationGroup.enabled(true)
        before.isEnabled = true
        after.isEnabled = true
        addBlank.isEnabled = true
        addBlankBefore.isEnabled = true
        if (fileLocations.isNotEmpty()) {
          for (fileLocation in fileLocations) {
            fileLocation.isEnabled = true
          }
        }
      }
    }
  }

  private fun enableFormattingOptions(enable: Boolean) {
    if (enable) {
      commentTypeGroup.enabled(true)
      borderGroup.enabled(true)
      blockComment.isEnabled = true
      lineComment.isEnabled = true
      prefixLines.isEnabled = allowBlock
      separatorBefore.isEnabled = true
      separatorAfter.isEnabled = true
      lengthBefore.isEnabled = separatorBefore.isSelected
      lengthBefore.isEnabled = separatorBefore.isSelected
      lengthAfter.isEnabled = separatorAfter.isSelected
      lengthAfter.isEnabled = separatorAfter.isSelected
      updateBox()
    }
    else {
      commentTypeGroup.enabled(false)
      borderGroup.enabled(false)
    }
  }

  private fun updateBox() {
    var enable = true
    if (!separatorBefore.isSelected || !separatorAfter.isSelected) {
      enable = false
    }
    else {
      if (lengthBefore.text != lengthAfter.text) {
        enable = false
      }
    }
    val either = separatorBefore.isSelected || separatorAfter.isSelected
    box.isEnabled = enable
    filler.isEnabled = either
  }

  private fun showPreview(options: LanguageOptions) {
    val defaultCopyrightText = if (noCopyright.isSelected) ""
    else FileTypeUtil
      .buildComment(fileType, VelocityHelper.evaluate(null, null, null, EntityUtil.decode(DEFAULT_COPYRIGHT_NOTICE)), options)
    SwingUtilities.invokeLater { preview.text = defaultCopyrightText }
  }

  private fun addOptionChangeListener(listener: TemplateOptionsPanelListener) {
    listeners.add(TemplateOptionsPanelListener::class.java, listener)
  }

  private fun fireChangeEvent() {
    val fires = listeners.listenerList
    var i = fires.size - 2
    while (i >= 0) {
      if (fires[i] === TemplateOptionsPanelListener::class.java) {
        (fires[i + 1] as TemplateOptionsPanelListener).optionChanged()
      }
      i -= 2
    }
  }

  private fun getCopyrightOptions(): Options {
    return getInstance(project).options
  }
}