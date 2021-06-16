// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewModuleStep.Companion.twoColumnRow
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import java.util.*
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingUtilities

class GroovyNewProjectWizard : NewProjectWizard<GroovyModuleSettings> {
  override val language: String = "Groovy"
  override var settingsFactory = { GroovyModuleSettings() }

  override fun settingsList(settings: GroovyModuleSettings): List<LabelAndComponent> {
    val panel = panel {
      row {
        row {
          label(GroovyBundle.message("label.groovy.library"))
        }
        row {
          lateinit var fromUrlCheckbox: CellBuilder<JBRadioButton>
          lateinit var fromFilesystemCheckbox: CellBuilder<JBRadioButton>
          twoColumnRow(
            { fromUrlCheckbox = radioButton(GroovyBundle.message("radio.use.version.from.maven"), settings::useMavenLibrary) },
            {
              val downloadableType = LibraryType.EP_NAME.findExtension(GroovyDownloadableLibraryType::class.java)!!
              val groovyLibraryDescription = downloadableType.libraryDescription
              comboBox(DefaultComboBoxModel(emptyArray()),
                       settings::version,
                       SimpleListCellRenderer.create("") { it?.get()?.versionString ?: "<unknown>" })
                .applyToComponent {
                  groovyLibraryDescription.fetchVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
                    override fun onSuccess(versions: List<FrameworkLibraryVersion>) = SwingUtilities.invokeLater {
                      for (item in versions) {
                        addItem(Optional.of(item))
                      }
                    }
                  })
                }.enableIf(fromUrlCheckbox.selected)
            })
          twoColumnRow(
            { fromFilesystemCheckbox = radioButton(GroovyBundle.message("radio.use.sdk.from.disk"), settings::useLocalLibrary) },
            {
              // todo: color in red if selected path does not correspond to a groovy sdk home
              textFieldWithBrowseButton(
                settings::sdkPath,
                GroovyBundle.message("dialog.title.select.groovy.sdk"),
                fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()).enableIf(
                fromFilesystemCheckbox.selected)
            }
          )
          fromFilesystemCheckbox.applyToComponent { addChangeListener { if (fromFilesystemCheckbox.selected()) fromUrlCheckbox.applyToComponent { isSelected = false } } }
          fromUrlCheckbox.applyToComponent { addChangeListener { if (fromUrlCheckbox.selected()) fromFilesystemCheckbox.applyToComponent { isSelected = false } } }
        }
      }
    }
    return listOf(LabelAndComponent(null, panel))
  }

  override fun setupProject(project: Project, settings: GroovyModuleSettings, context: WizardContext) {
    val builder = context.projectBuilder as? ModuleBuilder ?: return
    builder.contentEntryPath = project.basePath
    builder.name = project.name
    val groovyModuleBuilder = GroovyAwareModuleBuilder()
    val compositionSettings = LibraryCompositionSettings(GroovyLibraryDescription(), { project.basePath ?: "./" },
                                                         FrameworkLibraryVersionFilter.ALL, listOf(settings.version.get()))
    compositionSettings.setDownloadLibraries(true)

    groovyModuleBuilder.updateFrom(builder)
    if (settings.useMavenLibrary) {
      builder.addModuleConfigurationUpdater(object : ModuleBuilder.ModuleConfigurationUpdater() {
        override fun update(module: Module, rootModel: ModifiableRootModel) {
          val librariesContainer = if (context.modulesProvider == null)
            LibrariesContainerFactory.createContainer(context.project)
          else LibrariesContainerFactory.createContainer(context, context.modulesProvider)
          groovyModuleBuilder.setupRootModel(rootModel)
          compositionSettings.downloadFiles(context.wizard.contentComponent)
          compositionSettings.addLibraries(rootModel, mutableListOf(), librariesContainer)
        }
      })
    }
    else if (settings.useLocalLibrary) {
      //todo
    }

  }
}

class GroovyModuleSettings {
  var useMavenLibrary: Boolean = false
  var useLocalLibrary: Boolean = false
  var version: Optional<FrameworkLibraryVersion> = Optional.empty()
  var sdkPath: String = ""
}
