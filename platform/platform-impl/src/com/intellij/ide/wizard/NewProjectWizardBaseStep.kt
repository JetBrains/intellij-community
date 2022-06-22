// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.applyIf
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.Path

class NewProjectWizardBaseStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent), NewProjectWizardBaseData {
  override val nameProperty = propertyGraph.lazyProperty(::suggestName)
  override val pathProperty = propertyGraph.lazyProperty(::suggestLocation)

  override var name by nameProperty
  override var path by pathProperty

  internal var bottomGap: Boolean = true

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
        val locationProperty = pathProperty.joinCanonicalPath(nameProperty)
        textField()
          .bindText(nameProperty)
          .columns(COLUMNS_MEDIUM)
          .validationRequestor(AFTER_GRAPH_PROPAGATION(propertyGraph))
          .textValidation(CHECK_NON_EMPTY, CHECK_MODULE_NAME(context.project))
          .applyIf(context.isCreatingNewProject) { validation(CHECK_PROJECT_PATH(context.project, locationProperty)) }
          .applyIf(!context.isCreatingNewProject) { validation(CHECK_MODULE_PATH(context.project, locationProperty)) }
          .focused()
          .gap(RightGap.SMALL)
        installNameGenerators(getBuilderId(), nameProperty)
      }.bottomGap(BottomGap.SMALL)

      val locationRow = row(UIBundle.message("label.project.wizard.new.project.location")) {
        val commentProperty = pathProperty.joinCanonicalPath(nameProperty)
          .transform { getPathComment(it) }
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor().withFileFilter { it.isDirectory }
        val fileChosen = { file: VirtualFile -> getPresentablePath(file.path) }
        val title = IdeBundle.message("title.select.project.file.directory", context.presentationName)
        textFieldWithBrowseButton(title, context.project, fileChooserDescriptor, fileChosen)
          .bindText(pathProperty.toUiPathProperty())
          .horizontalAlign(HorizontalAlign.FILL)
          .textValidation(CHECK_NON_EMPTY, CHECK_DIRECTORY)
          .comment(commentProperty.get(), 100)
          .apply { commentProperty.afterChange { comment?.text = it } }
      }

      if (bottomGap) {
        locationRow.bottomGap(BottomGap.SMALL)
      }

      onApply {
        context.projectName = name
        context.setProjectFileDirectory(Path.of(path, name), false)
      }
    }
  }

  private fun getPathComment(canonicalPath: @NonNls String): @NlsContexts.DetailedDescription String {
    val shortPath = StringUtil.shortenPathWithEllipsis(getPresentablePath(canonicalPath), 60)
    return UIBundle.message("label.project.wizard.new.project.path.description", context.isCreatingNewProjectInt, shortPath)
  }

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

  init {
    data.putUserData(NewProjectWizardBaseData.KEY, this)
  }
}

fun newProjectWizardBaseStepWithoutGap(parent: NewProjectWizardStep): NewProjectWizardBaseStep {
  return NewProjectWizardBaseStep(parent).apply { bottomGap = false }
}