// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.NewModuleStep.Companion.twoColumnRow
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import org.jetbrains.plugins.groovy.GroovyBundle
import java.awt.Dimension
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JTextField
import javax.swing.SwingUtilities

class GroovyNewProjectWizard : NewProjectWizard {
  override val name: String = "Groovy"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(context: WizardContext) : NewProjectWizardStep(context) {
    private var javaSdk: Sdk? = null
    private var useMavenLibrary: Boolean = false
    private var useLocalLibrary: Boolean = false
    private var mavenVersion: FrameworkLibraryVersion? = null
    private var sdkPath: String = ""

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
          val sdkCombo = JdkComboBox(null, ProjectSdksModel().also { it.syncSdks() }, { it is JavaSdkType }, null, null, null)
            .apply { minimumSize = Dimension(0, 0) }
            .also { combo -> combo.addItemListener { javaSdk = combo.selectedJdk } }
          sdkCombo()
        }
        nestedPanel {
          row(GroovyBundle.message("label.groovy.sdk")) {
            buttonGroup {
              createDownloadableLibraryPanel()
              val disposable = createFileSystemLibraryPanel()
              Disposer.register(context.disposable, disposable)
            }
          }
        }
      }
    }

    private fun Row.createDownloadableLibraryPanel() {
      lateinit var checkbox: JBRadioButton
      twoColumnRow(
        {
          checkbox = radioButton(GroovyBundle.message("radio.use.version.from.maven"),
            ::useMavenLibrary).component.apply { isSelected = true }
        },
        {
          val groovyLibraryType = LibraryType.EP_NAME.findExtensionOrFail(GroovyDownloadableLibraryType::class.java)
          val downloadableLibraryDescription = groovyLibraryType.libraryDescription
          comboBox(
            DefaultComboBoxModel(emptyArray()),
            { mavenVersion }, { mavenVersion = it },
            SimpleListCellRenderer.create(GroovyBundle.message("combo.box.null.value.placeholder")) { it?.versionString ?: "<unknown>" }
          ).applyToComponent {
            downloadableLibraryDescription.fetchVersions(object : FileSetVersionsCallback<FrameworkLibraryVersion>() {
              override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
                versions.sortedWith(::moveUnstablesToTheEnd).forEach(::addItem)
              }
            })
          }.enableIf(checkbox.selected)
        })
    }

    private fun Row.createFileSystemLibraryPanel(): Disposable {
      lateinit var checkbox: JBRadioButton
      lateinit var disposable: Disposable
      twoColumnRow(
        { checkbox = radioButton(GroovyBundle.message("radio.use.sdk.from.disk"), ::useLocalLibrary).component },
        {
          // todo: color text field in red if selected path does not correspond to a groovy sdk home
          val groovyLibraryDescription = GroovyLibraryDescription()
          val fileChooserDescriptor = groovyLibraryDescription.createFileChooserDescriptor()
          val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
          val textWithBrowse = TextFieldWithBrowseButton()
            .apply {
              setText(pathToGroovyHome?.path)
              addActionListener(object : BrowseFolderActionListener<JTextField>(
                GroovyBundle.message("dialog.title.select.groovy.sdk"),
                null,
                this,
                null,
                fileChooserDescriptor,
                TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
                override fun getInitialFile() = pathToGroovyHome
              })
              FileChooserFactory.getInstance().installFileCompletion(textField, fileChooserDescriptor, true, null)
            }
            .let(::component)
            .constraints(growX)
            .withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, ::sdkPath.toBinding())
            .enableIf(checkbox.selected)

          disposable = textWithBrowse.component
        }
      )
      return disposable
    }

    override fun setupProject(project: Project) {
      val builder = context.projectBuilder as? ModuleBuilder ?: return
      val groovyModuleBuilder = GroovyAwareModuleBuilder().apply {
        contentEntryPath = project.basePath
        name = project.name
        moduleJdk = javaSdk
      }

      val librariesContainer = LibrariesContainerFactory.createContainer(context.project)

      val compositionSettings = generateCompositionSettings(project, librariesContainer)

      builder.addModuleConfigurationUpdater(object : ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
          groovyModuleBuilder.setupRootModel(rootModel)
          compositionSettings.addLibraries(rootModel, mutableListOf(), librariesContainer)
        }
      })
    }

    private fun generateCompositionSettings(project: Project, container: LibrariesContainer): LibraryCompositionSettings {
      val libraryDescription = GroovyLibraryDescription()
      // compositionSettings implements Disposable, but it is done that way because of the field with the type Library.
      // this field in our usage is always null, so there is no need to dispose compositionSettings
      val compositionSettings = LibraryCompositionSettings(libraryDescription, { project.basePath ?: "./" },
        FrameworkLibraryVersionFilter.ALL, listOf(mavenVersion))
      if (useMavenLibrary && mavenVersion != null) {
        compositionSettings.setDownloadLibraries(true)
        compositionSettings.downloadFiles(null)
      }
      else if (useLocalLibrary) {
        val virtualFile = VfsUtil.findFile(Path.of(sdkPath), false) ?: return compositionSettings
        val newLibraryConfiguration =
          libraryDescription.createLibraryConfiguration(getCurrentKeyboardFocusManager().activeWindow, virtualFile)
          ?: return compositionSettings
        val libraryEditor = NewLibraryEditor(newLibraryConfiguration.libraryType, newLibraryConfiguration.properties)
        libraryEditor.name = container.suggestUniqueLibraryName(newLibraryConfiguration.defaultLibraryName)
        newLibraryConfiguration.addRoots(libraryEditor)
        compositionSettings.setNewLibraryEditor(libraryEditor)
      }
      return compositionSettings
    }

    private fun moveUnstablesToTheEnd(left: FrameworkLibraryVersion, right: FrameworkLibraryVersion): Int {
      val leftVersion = left.versionString
      val rightVersion = right.versionString
      val leftUnstable = GroovyConfigUtils.isUnstable(leftVersion)
      val rightUnstable = GroovyConfigUtils.isUnstable(rightVersion)
      return when {
        leftUnstable == rightUnstable -> -GroovyConfigUtils.compareSdkVersions(leftVersion, rightVersion)
        leftUnstable -> 1
        else -> -1
      }
    }
  }
}
