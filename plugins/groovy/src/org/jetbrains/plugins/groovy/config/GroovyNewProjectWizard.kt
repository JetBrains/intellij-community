// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.*
import com.intellij.ide.NewModuleStep.Companion.twoColumnRow
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions.FileSetVersionsCallback
import com.intellij.util.ui.update.LazyUiDisposable
import org.jetbrains.plugins.groovy.GroovyBundle
import java.awt.Dimension
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JTextField
import javax.swing.SwingUtilities

class GroovyNewProjectWizard : NewProjectWizard<GroovyModuleSettings> {
  override val language: String = "Groovy"
  override var settingsFactory = { GroovyModuleSettings() }

  override fun settingsList(settings: GroovyModuleSettings): List<SettingsComponent> {
    val sdkCombo = JdkComboBox(null, ProjectSdksModel(), { it is JavaSdk }, null, null, null)
      .apply { minimumSize = Dimension(0, 0) }
      .also { combo -> combo.addItemListener { settings.javaSdk = combo.selectedJdk } }
    lateinit var disposableComponent: Disposable
    val panel = panel {
      row {
        val fromUrlCheckbox: JBRadioButton = createDownloadableLibraryPanel(settings)
        val (fromFilesystemCheckbox, innerDisposableComponent) = createFileSystemLibraryPanel(settings)
        disposableComponent = innerDisposableComponent

        fromFilesystemCheckbox.addChangeListener {
          if (fromFilesystemCheckbox.selected()) fromUrlCheckbox.isSelected = false
        }
        fromUrlCheckbox.addChangeListener {
          if (fromUrlCheckbox.selected()) fromFilesystemCheckbox.isSelected = false
        }
      }
    }

    object : LazyUiDisposable<Disposable>(null, panel, disposableComponent) {
      override fun initialize(parent: Disposable, child: Disposable, project: Project?) {
      }
    }

    return listOf(LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo),
                  LabelAndComponent(JBLabel(GroovyBundle.message("label.groovy.sdk")), panel {}),
                  JustComponent(panel))
  }

  private fun Row.createDownloadableLibraryPanel(settings: GroovyModuleSettings): JBRadioButton {
    lateinit var checkbox: JBRadioButton
    twoColumnRow(
      { checkbox = radioButton(GroovyBundle.message("radio.use.version.from.maven"), settings::useMavenLibrary).component },
      {
        val groovyLibraryType = LibraryType.EP_NAME.findExtensionOrFail(GroovyDownloadableLibraryType::class.java)
        val downloadableLibraryDescription = groovyLibraryType.libraryDescription
        comboBox(
          DefaultComboBoxModel(emptyArray()),
          { settings.mavenVersion }, { settings.mavenVersion = it },
          SimpleListCellRenderer.create(GroovyBundle.message("combo.box.null.value.placeholder")) { it?.versionString ?: "<unknown>" }
        ).applyToComponent {
          downloadableLibraryDescription.fetchVersions(object : FileSetVersionsCallback<FrameworkLibraryVersion>() {
            override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
              versions.sortedWith(::moveUnstablesToTheEnd).forEach(::addItem)
            }
          })
        }.enableIf(checkbox.selected)
      })
    return checkbox
  }

  private fun Row.createFileSystemLibraryPanel(settings: GroovyModuleSettings): Pair<JBRadioButton, Disposable> {
    lateinit var checkbox: JBRadioButton
    lateinit var disposable: Disposable
    twoColumnRow(
      { checkbox = radioButton(GroovyBundle.message("radio.use.sdk.from.disk"), settings::useLocalLibrary).component },
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
          .withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, settings::sdkPath.toBinding())
          .enableIf(checkbox.selected)

        disposable = textWithBrowse.component
      }
    )
    return checkbox to disposable
  }

  override fun setupProject(project: Project, settings: GroovyModuleSettings, context: WizardContext) {
    val builder = context.projectBuilder as? ModuleBuilder ?: return
    val groovyModuleBuilder = GroovyAwareModuleBuilder().apply {
      contentEntryPath = project.basePath
      name = project.name
      moduleJdk = settings.javaSdk
    }

    val librariesContainer = LibrariesContainerFactory.createContainer(context.project)

    val compositionSettings = generateCompositionSettings(settings, project, librariesContainer)

    builder.addModuleConfigurationUpdater(object : ModuleConfigurationUpdater() {
      override fun update(module: Module, rootModel: ModifiableRootModel) {
        groovyModuleBuilder.setupRootModel(rootModel)
        compositionSettings.addLibraries(rootModel, mutableListOf(), librariesContainer)
      }
    })
  }
}

private fun generateCompositionSettings(settings: GroovyModuleSettings,
                                        project: Project,
                                        container: LibrariesContainer): LibraryCompositionSettings {
  val libraryDescription = GroovyLibraryDescription()
  // compositionSettings implements Disposable, but it is done that way because of the field with the type Library.
  // this field in our usage is always null, so there is no need to dispose compositionSettings
  val compositionSettings = LibraryCompositionSettings(libraryDescription, { project.basePath ?: "./" },
                                                       FrameworkLibraryVersionFilter.ALL, listOf(settings.mavenVersion))
  if (settings.useMavenLibrary && settings.mavenVersion != null) {
    compositionSettings.setDownloadLibraries(true)
    compositionSettings.downloadFiles(null)
  }
  else if (settings.useLocalLibrary) {
    val virtualFile = VfsUtil.findFile(Path.of(settings.sdkPath), false) ?: return compositionSettings
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

class GroovyModuleSettings {
  var javaSdk: Sdk? = null
  var useMavenLibrary: Boolean = false
  var useLocalLibrary: Boolean = false
  var mavenVersion: FrameworkLibraryVersion? = null
  var sdkPath: String = ""
}
