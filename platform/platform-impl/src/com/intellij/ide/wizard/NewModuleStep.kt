// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel

abstract class NewModuleStep(context: WizardContext) : ModuleWizardStep() {

  abstract val steps: List<NewProjectWizardStep>

  private val panelBuilder = NewProjectWizardPanelBuilder(context)

  override fun validate() = panelBuilder.validate()

  override fun updateDataModel() = panelBuilder.apply()

  override fun getPreferredFocusedComponent() = panelBuilder.preferredFocusedComponent

  override fun getComponent() =
    panelBuilder.panel { steps.forEach { it.setupUI(this) } }
      .apply { withBorder(JBUI.Borders.empty(20, 20)) }
      .also { fixUiShiftingWhenChoosingMultiStep(it) }

  private fun fixUiShiftingWhenChoosingMultiStep(panel: DialogPanel) {
    val labels = UIUtil.uiTraverser(panel)
      .filterIsInstance<JLabel>()
      .filter { it.parent is DialogPanel }
      .filter { it.getGapBefore() == null }
    val width = labels.maxOf { it.preferredSize.width }
    labels.forEach { it.setMinimumWidth(width) }
  }

  private fun JComponent.getConstraints(): CC? {
    val layout = parent.layout as? MigLayout ?: return null
    return layout.getComponentConstraints()[this]
  }

  private fun JComponent.getGapBefore(): BoundSize? {
    return getConstraints()?.horizontal?.gapBefore
  }

  private fun JComponent.setMinimumWidth(width: Int) {
    minimumSize = minimumSize.apply { this.width = width }
  }

  fun setupProject(project: Project) {
    steps.forEach { it.setupProject(project) }
  }

  class Step(override val context: WizardContext) : NewProjectWizardStep, NewProjectWizardData {

    override val propertyGraph = PropertyGraph("New project wizard")

    override val nameProperty = propertyGraph.graphProperty { suggestName() }
    override val pathProperty = propertyGraph.graphProperty { context.projectFileDirectory }
    override val gitProperty = propertyGraph.graphProperty { false }

    override var name by nameProperty
    override var path by pathProperty
    override var git by gitProperty

    override val projectPath: Path get() = Path.of(path, name)

    private fun suggestName(): String {
      val moduleNames = findAllModules().map { it.name }.toSet()
      return FileUtil.createSequentFileName(File(path), "untitled", "") {
        !it.exists() && it.name !in moduleNames
      }
    }

    private fun findAllModules(): List<Module> {
      val project = context.project ?: return emptyList()
      val moduleManager = ModuleManager.getInstance(project)
      return moduleManager.modules.toList()
    }

    override fun setupUI(builder: LayoutBuilder) {
      with(builder) {
        row(UIBundle.message("label.project.wizard.new.project.name")) {
          textField(nameProperty)
            .withValidationOnApply { validateName() }
            .withValidationOnInput { validateName() }
            .growPolicy(GrowPolicy.SHORT_TEXT)
            .focused()
          installNameGenerators(getBuilderId(), nameProperty)
        }.largeGapAfter()
        row(UIBundle.message("label.project.wizard.new.project.location")) {
          val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter { it.isDirectory }
          val fileChosen = { file: VirtualFile -> getPresentablePath(file.path) }
          val title = IdeBundle.message("title.select.project.file.directory", context.presentationName)
          val uiPathProperty = pathProperty.transform(::getPresentablePath, ::getCanonicalPath)
          textFieldWithBrowseButton(uiPathProperty, title, context.project, fileChooserDescriptor, fileChosen)
            .withValidationOnApply { validateLocation() }
            .withValidationOnInput { validateLocation() }
        }.largeGapAfter()
        row("") {
          checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), gitProperty)
        }.largeGapAfter()

        onGlobalApply {
          context.projectName = name
          context.setProjectFileDirectory(projectPath, false)
        }
      }
    }

    private fun getBuilderId(): String? {
      val projectBuilder = context.projectBuilder
      if (projectBuilder is NewWizardModuleBuilder) {
        return projectBuilder.builderId
      }
      return null
    }

    private fun ValidationInfoBuilder.validateName(): ValidationInfo? {
      if (name.isEmpty()) {
        return error(UIBundle.message("label.project.wizard.new.project.missing.name.error", if (context.isCreatingNewProject) 1 else 0))
      }
      if (name in findAllModules().map { it.name }.toSet()) {
        return error(UIBundle.message("label.project.wizard.new.project.name.exists.error", if (context.isCreatingNewProject) 1 else 0, name))
      }
      return null
    }

    private fun ValidationInfoBuilder.validateLocation(): ValidationInfo? {
      if (path.isEmpty()) {
        return error(UIBundle.message("label.project.wizard.new.project.missing.path.error", if (context.isCreatingNewProject) 1 else 0))
      }

      val projectPath = try {
        projectPath
      }
      catch (ex: InvalidPathException) {
        return error(UIBundle.message("label.project.wizard.new.project.directory.invalid", ex.reason))
      }
      for (project in ProjectManager.getInstance().openProjects) {
        if (ProjectUtil.isSameProject(projectPath, project)) {
          return error(UIBundle.message("label.project.wizard.new.project.directory.already.taken.error", project.name))
        }
      }

      val file = projectPath.toFile()
      if (file.exists()) {
        if (!file.canWrite()) {
          return error(UIBundle.message("label.project.wizard.new.project.directory.not.writable.error"))
        }
        val children = file.list()
        if (children == null) {
          return error(UIBundle.message("label.project.wizard.new.project.file.not.directory.error"))
        }
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          if (children.isNotEmpty()) {
            return warning(UIBundle.message("label.project.wizard.new.project.directory.not.empty.warning"))
          }
        }
      }
      return null
    }

    override fun setupProject(project: Project) {}
  }
}
