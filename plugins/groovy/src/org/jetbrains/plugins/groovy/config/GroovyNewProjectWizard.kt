// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.*
import com.intellij.ide.NewModuleStep.Companion.twoColumnRow
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.ItemListener
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
      .also { combo -> combo.addItemListener(ItemListener { settings.javaSdk = combo.selectedJdk }) }
    val panel = panel {
      row {
        lateinit var fromUrlCheckbox: CellBuilder<JBRadioButton>
        lateinit var fromFilesystemCheckbox: CellBuilder<JBRadioButton>
        twoColumnRow(
          { fromUrlCheckbox = radioButton(GroovyBundle.message("radio.use.version.from.maven"), settings::useMavenLibrary) },
          {
            val downloadableType = LibraryType.EP_NAME.findExtension(GroovyDownloadableLibraryType::class.java)!!
            val groovyLibraryDescription = downloadableType.libraryDescription
            comboBox(DefaultComboBoxModel(emptyArray()),
                     { settings.mavenVersion }, { settings.mavenVersion = it },
                     SimpleListCellRenderer.create("") { it?.versionString ?: "<unknown>" })
              .applyToComponent {
                groovyLibraryDescription.fetchVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
                  override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
                    for (item in versions) {
                      addItem(item)
                    }
                  }
                })
              }.enableIf(fromUrlCheckbox.selected)
          })
        twoColumnRow(
          { fromFilesystemCheckbox = radioButton(GroovyBundle.message("radio.use.sdk.from.disk"), settings::useLocalLibrary) },
          {
            // todo: color text field in red if selected path does not correspond to a groovy sdk home
            val groovyLibraryDescription = GroovyLibraryDescription()
            val fileChooserDescriptor = groovyLibraryDescription.createFileChooserDescriptor()
            val textWithBrowse = TextFieldWithBrowseButton()
            val pathToGroovyHome = groovyLibraryDescription.findPathToGroovyHome()
            textWithBrowse.setText(pathToGroovyHome?.path)
            textWithBrowse.addActionListener(object : ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(
              GroovyBundle.message("dialog.title.select.groovy.sdk"), null, textWithBrowse, null, fileChooserDescriptor,
              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
              override fun getInitialFile(): VirtualFile? {
                return pathToGroovyHome
              }
            })
            FileChooserFactory.getInstance().installFileCompletion(textWithBrowse.textField, fileChooserDescriptor, true, null)
            val wrappedComponent = component(textWithBrowse)
            wrappedComponent.constraints(growX)
              .withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, settings::sdkPath.toBinding())
              .enableIf(fromFilesystemCheckbox.selected)
          }
        )
        fromFilesystemCheckbox.applyToComponent { addChangeListener { if (fromFilesystemCheckbox.selected()) fromUrlCheckbox.applyToComponent { isSelected = false } } }
        fromUrlCheckbox.applyToComponent { addChangeListener { if (fromUrlCheckbox.selected()) fromFilesystemCheckbox.applyToComponent { isSelected = false } } }
      }
    }
    return listOf(LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo), LabelAndComponent(JBLabel(GroovyBundle.message("label.groovy.sdk")), panel {}), JustComponent(panel))
  }

  override fun setupProject(project: Project, settings: GroovyModuleSettings, context: WizardContext) {
    val builder = context.projectBuilder as? ModuleBuilder ?: return
    builder.contentEntryPath = project.basePath
    builder.name = project.name
    val groovyModuleBuilder = GroovyAwareModuleBuilder()
    val libraryDescription = GroovyLibraryDescription()
    val compositionSettings = LibraryCompositionSettings(libraryDescription, { project.basePath ?: "./" },
                                                         FrameworkLibraryVersionFilter.ALL, listOf(settings.mavenVersion))
    builder.moduleJdk = settings.javaSdk
    groovyModuleBuilder.updateFrom(builder)
    groovyModuleBuilder.moduleJdk = settings.javaSdk
    println(settings.javaSdk)
    val librariesContainer = if (context.modulesProvider == null)
      LibrariesContainerFactory.createContainer(context.project)
    else LibrariesContainerFactory.createContainer(context, context.modulesProvider)
    if (settings.useMavenLibrary && settings.mavenVersion != null) {
      compositionSettings.setDownloadLibraries(true)
      compositionSettings.downloadFiles(context.wizard.contentComponent)
    }
    else if (settings.useLocalLibrary) {
      val virtualFile = VfsUtil.findFile(Path.of(settings.sdkPath), false) ?: return
      val newLibraryConfiguration = libraryDescription.createLibraryConfiguration(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow, virtualFile) ?: return
      val libraryEditor = NewLibraryEditor(newLibraryConfiguration.libraryType, newLibraryConfiguration.properties)
      libraryEditor.name = librariesContainer.suggestUniqueLibraryName(newLibraryConfiguration.defaultLibraryName)
      newLibraryConfiguration.addRoots(libraryEditor)
      compositionSettings.setNewLibraryEditor(libraryEditor)
    }
    builder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
      override fun update(module: Module, rootModel: ModifiableRootModel) {
        groovyModuleBuilder.setupRootModel(rootModel)
        compositionSettings.addLibraries(rootModel, mutableListOf(), librariesContainer)
      }
    })
  }
}

class GroovyModuleSettings {
  var javaSdk: Sdk? = null
  var useMavenLibrary: Boolean = false
  var useLocalLibrary: Boolean = false
  var mavenVersion: FrameworkLibraryVersion? = null
  var sdkPath: String = ""
}
