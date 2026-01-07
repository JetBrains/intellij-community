// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.configurable.VcsDirectoryConfigurationPanel.Companion.buildVcsesComboBox
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vcs.impl.VcsDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.dialog.VcsDialogUtils.getMorePluginsLink
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField

internal class VcsMappingConfigurationDialog(
  private val project: Project,
  @NlsContexts.DialogTitle title: String,
) : DialogWrapper(project, false) {

  private val vcsManager: ProjectLevelVcsManager = ProjectLevelVcsManager.getInstance(project)

  private val vcsComboBox: ComboBox<AbstractVcs?> = buildVcsesComboBox(project)
  private val directoryTextField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
  private val vcsConfigurablePlaceholder: JPanel = JPanel(BorderLayout())

  private val directoryRadioButton: JRadioButton = JRadioButton(VcsBundle.message("vcs.common.labels.directory"))
  private val projectRadioButton: JRadioButton = JRadioButton()

  private var vcsConfigurable: UnnamedConfigurable? = null
  private var vcsConfigurableComponent: JComponent? = null
  private var mappingCopy: VcsDirectoryMapping = VcsDirectoryMapping("", "")

  init {
    directoryRadioButton.isSelected = true

    val listener = ActionListener { directoryTextField.isEnabled = directoryRadioButton.isSelected }
    projectRadioButton.addActionListener(listener)
    directoryRadioButton.addActionListener(listener)

    val descriptor = FileChooserDescriptorFactory.singleDir()
      .withTitle(VcsBundle.message("settings.vcs.mapping.browser.select.directory.title"))
      .withDescription(VcsBundle.message("settings.vcs.mapping.browser.select.directory.description"))
    directoryTextField.addActionListener(MyBrowseFolderListener(directoryTextField, project, descriptor))

    setTitle(title)
    setOKButtonText(VcsBundle.message("directory.mapping.save.button"))
    init()

    setMapping(suggestDefaultMapping(project))
    vcsComboBox.addActionListener { updateVcsConfigurable() }
  }

  fun getMapping(): VcsDirectoryMapping {
    val vcs = vcsComboBox.item
    val vcsName = vcs?.name ?: ""
    val directory = if (projectRadioButton.isSelected) "" else toSystemIndependentName(directoryTextField.text)
    return VcsDirectoryMapping(directory, vcsName, mappingCopy.rootSettings)
  }

  fun setMapping(mapping: VcsDirectoryMapping) {
    mappingCopy = VcsDirectoryMapping(mapping.directory, mapping.vcs, mapping.rootSettings)
    projectRadioButton.isSelected = mappingCopy.isDefaultMapping
    directoryRadioButton.isSelected = !projectRadioButton.isSelected
    directoryTextField.text = if (mappingCopy.isDefaultMapping) "" else toSystemDependentName(mapping.directory)
    directoryTextField.isEnabled = directoryRadioButton.isSelected

    vcsComboBox.selectedItem = vcsManager.findVcsByName(mapping.vcs)
    updateVcsConfigurable()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(VcsBundle.message("vcs.common.labels.version.control")) {
      cell(vcsComboBox)
        .resizableColumn()
        .align(AlignX.FILL)
      cell(getMorePluginsLink(contentPanel, Runnable { close(CANCEL_EXIT_CODE) }))
    }.layout(RowLayout.LABEL_ALIGNED)

    buttonsGroup {
      row {
        cell(directoryRadioButton)
        cell(directoryTextField)
          .resizableColumn()
          .align(AlignX.FILL)
      }.layout(RowLayout.LABEL_ALIGNED)
      row {
        cell(projectRadioButton)
          .applyToComponent {
            text = DefaultVcsRootPolicy.getInstance(project).getProjectMappingInDialogDescription()
          }
      }
    }

    row {
      cell(vcsConfigurablePlaceholder)
        .align(Align.FILL)
    }.resizableRow()
  }

  private fun updateVcsConfigurable() {
    vcsConfigurable?.let { configurable ->
      vcsConfigurableComponent?.let(vcsConfigurablePlaceholder::remove)
      configurable.disposeUIResources()
      vcsConfigurable = null
    }

    vcsComboBox.item?.getRootConfigurable(mappingCopy)
      ?.let { newConfigurable ->
        vcsConfigurable = newConfigurable
        vcsConfigurableComponent = newConfigurable.createComponent()
          ?.also { vcsConfigurablePlaceholder.add(it, BorderLayout.CENTER) }
      }
    pack()
  }

  override fun doOKAction() {
    try {
      vcsConfigurable?.apply()
    }
    catch (ex: ConfigurationException) {
      Messages.showErrorDialog(contentPanel, VcsBundle.message("settings.vcs.mapping.invalid.vcs.options.error", ex.getMessageHtml()))
      return
    }
    super.doOKAction()
  }

  private inner class MyBrowseFolderListener(
    textField: TextFieldWithBrowseButton,
    project: Project,
    fileChooserDescriptor: FileChooserDescriptor,
  ) : BrowseFolderActionListener<JTextField>(textField, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

    override fun getInitialFile(): VirtualFile? {
      // suggest project base dir only if nothing is typed in the component
      if (componentText.isEmpty()) {
        return project?.guessProjectDir()
      }
      return super.getInitialFile()
    }

    override fun onFileChosen(chosenFile: VirtualFile) {
      val oldText = directoryTextField.text
      super.onFileChosen(chosenFile)
      val vcs = vcsComboBox.item
      if (oldText.isEmpty() && vcs != null) {
        object : Task.Backgroundable(this@VcsMappingConfigurationDialog.project,
                                     VcsBundle.message("settings.vcs.mapping.status.looking.for.vcs.administrative.area"),
                                     true) {
          private var probableVcs: VcsDescriptor? = null

          override fun run(indicator: ProgressIndicator) {
            val allVcss = vcsManager.getAllVcss().toList()
            probableVcs = allVcss.single { descriptor -> descriptor.probablyUnderVcs(chosenFile) }
          }

          override fun onSuccess() {
            probableVcs?.let { vcsComboBox.selectedItem = it }
          }
        }.queue()
      }
    }
  }

  companion object {
    private fun suggestDefaultMapping(project: Project): VcsDirectoryMapping {
      val vcses = ProjectLevelVcsManager.getInstance(project).getAllSupportedVcss()
      ContainerUtil.sort(vcses, SuggestedVcsComparator.create(project))
      val defaultVcsName = vcses.firstOrNull()?.name.orEmpty()

      val basePath = project.basePath ?: return VcsDirectoryMapping.createDefault(defaultVcsName)
      return VcsDirectoryMapping(basePath, defaultVcsName)
    }
  }
}
