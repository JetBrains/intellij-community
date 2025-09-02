// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.IdeBundle.message
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLocationChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logNameChanged
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.util.getTextWidth
import com.intellij.util.applyIf
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Handles the project **Name** and **Location** fields.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html">New Project Wizard API (IntelliJ Platform Docs)</a>
 */
class NewProjectWizardBaseStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent), NewProjectWizardBaseData {
  override val nameProperty: GraphProperty<String> = propertyGraph.lazyProperty(::suggestName)
  override val pathProperty: GraphProperty<String> = propertyGraph.lazyProperty { suggestLocation().toCanonicalPath() }

  override var name: String by nameProperty
  override var path: String by pathProperty

  var defaultName: String = "untitled"

  internal var bottomGap: Boolean = true

  private fun suggestLocation(): Path {
    val location = context.projectDirectory
    if (context.isCreatingNewProject) {
      return location
    }
    if (isModuleDirectory(location)) {
      return location
    }
    val parentLocation = location.parent
    if (parentLocation == null) {
      return location
    }
    return parentLocation
  }

  private fun suggestName(): String {
    val location = context.projectDirectory
    if (path == location.parent?.toCanonicalPath()) {
      return location.name
    }
    return suggestUniqueName()
  }

  private fun suggestUniqueName(): String {
    val moduleNames = findAllModules().map { it.name }.toSet()
    val path = path.toNioPathOrNull() ?: return defaultName
    return FileUtil.createSequentFileName(path.toFile(), defaultName, "") {
      !it.exists() && it.name !in moduleNames
    }
  }

  private fun findAllModules(): List<Module> {
    val project = context.project ?: return emptyList()
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.modules.toList()
  }

  private fun isModuleDirectory(path: Path): Boolean {
    return findAllModules().asSequence()
      .flatMap { it.rootManager.contentRoots.asSequence() }
      .any { it.isDirectory && it.toNioPathOrNull() == path }
  }

  init {
    nameProperty.dependsOn(pathProperty, ::suggestUniqueName)
  }

  override fun setupUI(builder: Panel) {
    val locationProperty = pathProperty.joinCanonicalPath(nameProperty)
    with(builder) {
      row(UIBundle.message("label.project.wizard.new.project.name")) {
        textField()
          .bindText(nameProperty.trim())
          .columns(COLUMNS_MEDIUM)
          .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
          .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_MODULE_NAME(context.project))
          .applyIf(context.isCreatingNewProject) { validation(CHECK_PROJECT_PATH(context.project, locationProperty)) }
          .applyIf(!context.isCreatingNewProject) { validation(CHECK_MODULE_PATH(context.project, locationProperty)) }
          .focused()
          .gap(RightGap.SMALL)
          .whenTextChangedFromUi { logNameChanged() }
        installNameGenerators(getBuilderId(), nameProperty)
      }.bottomGap(BottomGap.SMALL)

      val locationRow = row(UIBundle.message("label.project.wizard.new.project.location")) {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          .withTitle(message("title.select.project.file.directory", context.presentationName))
          .withPathToTextConvertor(::getPresentablePath)
          .withTextToPathConvertor(::getCanonicalPath)
        textFieldWithBrowseButton(fileChooserDescriptor, context.project)
          .bindText(pathProperty.toUiPathProperty())
          .align(AlignX.FILL)
          .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_DIRECTORY)
          .whenTextChangedFromUi { logLocationChanged() }
          .locationComment(context, locationProperty)
      }

      if (bottomGap) {
        locationRow.bottomGap(BottomGap.SMALL)
      }

      onApply {
        context.projectName = name
        context.setProjectFileDirectory(Path.of(path).resolve(name), false)
      }
    }
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

  companion object {

    /**
     * Defines ration between location text field width and location comment width.
     * Cannot be 1.0 or more because:
     *  1. Comment width is dependent on location text field width;
     *  2. Minimum location text field width cannot be less comment width.
     * So this ratio makes a gap between minimum widths of comment and location.
     * It allows smoothly resizing components (location and comment) when the NPW dialog is resized.
     */
    private const val LOCATION_COMMENT_RATIO = 0.9f

    private fun Cell<TextFieldWithBrowseButton>.locationComment(context: WizardContext, locationProperty: ObservableProperty<String>) {
      comment("", MAX_LINE_LENGTH_NO_WRAP)
      val comment = comment!!
      val widthProperty = component.widthProperty
      val commentProperty = operation(locationProperty, widthProperty) { path, width ->
        val isCreatingNewProjectInt = context.isCreatingNewProjectInt
        val commentWithEmptyPath = UIBundle.message("label.project.wizard.new.project.path.description", isCreatingNewProjectInt, "")
        val commentWidthWithEmptyPath = comment.getTextWidth(commentWithEmptyPath)
        val maxPathWidth = (LOCATION_COMMENT_RATIO * width).toInt() - commentWidthWithEmptyPath
        val presentablePath = getPresentablePath(path)
        val shortPresentablePath = shortenTextWithEllipsis(
          text = presentablePath,
          maxTextWidth = maxPathWidth,
          getTextWidth = comment::getTextWidth,
        )
        UIBundle.message("label.project.wizard.new.project.path.description", isCreatingNewProjectInt, shortPresentablePath)
      }
      comment.bind(commentProperty)
    }
  }
}

fun newProjectWizardBaseStepWithoutGap(parent: NewProjectWizardStep): NewProjectWizardBaseStep {
  return NewProjectWizardBaseStep(parent).apply { bottomGap = false }
}
