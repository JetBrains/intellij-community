// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.configurable.VcsDirectoryConfigurationPanel.Companion.buildVcsesComboBox
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.dialog.VcsDialogUtils.getMorePluginsLink
import com.intellij.vcs.VcsDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  private val coroutineScope = VcsDisposable.getInstance(project).childScope("VcsMappingConfigurationDialog", disposable)

  private val vcsComboBox: ComboBox<AbstractVcs?> = buildVcsesComboBox(project)
  private val directoryTextField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
  private val content = VcsMappingDialogContent()

  private val directoryRadioButton: JRadioButton = JRadioButton(VcsBundle.message("vcs.common.labels.directory"))
  private val projectRadioButton: JRadioButton = JRadioButton()

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
    val vcsName = vcs?.name.orEmpty()
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
      cell(getMorePluginsLink(contentPanel) { close(CANCEL_EXIT_CODE) })
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
      cell(content.mainPanel)
        .align(Align.FILL)
    }.resizableRow()
  }

  private fun updateVcsConfigurable() {
    val newConfigurable = vcsComboBox.item?.getRootConfigurable(mappingCopy)
    content.update(newConfigurable)
    pack()
  }

  override fun doOKAction() {
    try {
      content.apply()
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
        coroutineScope.launch {
          withBackgroundProgress(this@VcsMappingConfigurationDialog.project,
                                 VcsBundle.message("settings.vcs.mapping.status.looking.for.vcs.administrative.area")) {
            val allVcss = vcsManager.getAllVcss()
            val probableVcs = allVcss.firstOrNull { descriptor -> descriptor.probablyUnderVcs(chosenFile) }
            withContext(Dispatchers.EDT) {
              probableVcs?.let { vcsComboBox.selectedItem = it }
            }
          }
        }
      }
    }
  }

  private class VcsMappingDialogContent {
    val mainPanel: JPanel = JPanel(BorderLayout())
    private var configurable: UnnamedConfigurable? = null
    private var component: JComponent? = null

    fun update(newConfigurable: UnnamedConfigurable?) {
      dispose()
      configurable = newConfigurable
      component = newConfigurable?.createComponent()?.also {
        mainPanel.add(it, BorderLayout.CENTER)
      }
    }

    fun dispose() {
      component?.let(mainPanel::remove)
      configurable?.disposeUIResources()
      configurable = null
      component = null
    }

    fun apply() {
      configurable?.apply()
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
