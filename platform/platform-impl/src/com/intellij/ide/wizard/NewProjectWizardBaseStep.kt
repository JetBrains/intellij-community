// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path


class NewProjectWizardBaseStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent), NewProjectWizardBaseData {
  override val nameProperty = propertyGraph.lazyProperty(::suggestName)
  override val pathProperty = propertyGraph.lazyProperty(::suggestLocation)

  override var name by nameProperty
  override var path by pathProperty

  private fun suggestLocation(): String {
    val location = context.projectFileDirectory
    if (context.isCreatingNewProject) {
      return location
    }
    if (isModuleDirectory(location)) {
      return location
    }
    val parentLocation = File(location).parent
    if (parentLocation == null) {
      return location
    }
    return getCanonicalPath(parentLocation)
  }

  private fun suggestName(): String {
    val location = context.projectFileDirectory
    if (FileUtil.pathsEqual(File(location).parent, path)) {
      return File(location).name
    }
    return suggestUniqueName()
  }

  private fun suggestUniqueName(): String {
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

  private fun isModuleDirectory(path: String): Boolean {
    return findAllModules().asSequence()
      .flatMap { it.rootManager.contentRoots.asSequence() }
      .any { it.isDirectory && FileUtil.pathsEqual(it.path, path) }
  }

  init {
    nameProperty.dependsOn(pathProperty, ::suggestUniqueName)
  }

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(UIBundle.message("label.project.wizard.new.project.name")) {
        textField()
          .bindText(nameProperty)
          .columns(COLUMNS_MEDIUM)
          .validationOnApply { validateNameAndLocation() }
          .validationOnInput { validateNameAndLocation() }
          .focused()
        installNameGenerators(getBuilderId(), nameProperty)
      }.bottomGap(BottomGap.SMALL)
      row(UIBundle.message("label.project.wizard.new.project.location")) {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter { it.isDirectory }
        val fileChosen = { file: VirtualFile -> getPresentablePath(file.path) }
        val title = IdeBundle.message("title.select.project.file.directory", context.presentationName)
        val uiPathProperty = pathProperty.toUiPathProperty()
        val comment = textFieldWithBrowseButton(title, context.project, fileChooserDescriptor, fileChosen)
          .bindText(uiPathProperty)
          .horizontalAlign(HorizontalAlign.FILL)
          .validationOnApply { validateLocation() }
          .validationOnInput { validateLocation() }
          .comment(getComment(), 100)
          .comment!!
        nameProperty.afterChange { comment.text = getComment() }
        pathProperty.afterChange { comment.text = getComment() }
      }.bottomGap(BottomGap.SMALL)

      onApply {
        context.projectName = name
        context.setProjectFileDirectory(Path.of(path, name), false)
      }
    }
  }

  private fun getComment() = UIBundle.message("label.project.wizard.new.project.path.description",
                                              StringUtil.shortenPathWithEllipsis(Path.of(path, name).toString(), 60))

  override fun setupProject(project: Project) {
    if (context.isCreatingNewProject) {
      invokeAndWaitIfNeeded {
        runWriteAction {
          val projectManager = ProjectRootManager.getInstance(project)
          projectManager.projectSdk = context.projectJdk
        }
      }
    }
  }

  private fun getBuilderId(): String? {
    val projectBuilder = context.projectBuilder
    if (projectBuilder is ModuleBuilder) {
      return projectBuilder.builderId
    }
    return null
  }

  private fun ValidationInfoBuilder.validateNameAndLocation(): ValidationInfo? {
    if (name.isEmpty()) {
      return error(UIBundle.message("label.project.wizard.new.project.missing.name.error", context.isCreatingNewProjectInt))
    }
    if (name in findAllModules().map { it.name }.toSet()) {
      return error(UIBundle.message("label.project.wizard.new.project.name.exists.error", context.isCreatingNewProjectInt, name))
    }

    if (validateLocation() == null) {
      val projectPath = try {
        Path.of(path, name)
      }
      catch (ex: InvalidPathException) {
        return error(UIBundle.message("label.project.wizard.new.project.directory.invalid", ex.reason))
      }

      if (context.isCreatingNewProject) {
        for (project in ProjectManager.getInstance().openProjects) {
          if (ProjectUtil.isSameProject(projectPath, project)) {
            return error(UIBundle.message("label.project.wizard.new.project.directory.already.taken.error", project.name))
          }
        }
      }

      val file = projectPath.toFile()
      if (file.exists()) {
        if (!file.canWrite()) {
          return error(UIBundle.message("label.project.wizard.new.project.directory.not.writable.error", name))
        }
        val children = file.list()
        if (children == null) {
          return error(UIBundle.message("label.project.wizard.new.project.file.not.directory.error", name))
        }
        if (children.isNotEmpty()) {
          return warning(UIBundle.message("label.project.wizard.new.project.directory.not.empty.warning", name))
        }
      }
    }
    return null
  }

  private fun ValidationInfoBuilder.validateLocation(): ValidationInfo? {
    if (path.isEmpty()) {
      return error(UIBundle.message("label.project.wizard.new.project.missing.path.error", context.isCreatingNewProjectInt))
    }
    try {
      Path.of(path)
    }
    catch (ex: InvalidPathException) {
      return error(UIBundle.message("label.project.wizard.new.project.directory.invalid", ex.reason))
    }
    return null
  }

  init {
    data.putUserData(NewProjectWizardBaseData.KEY, this)
  }
}