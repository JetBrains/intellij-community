package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EventDispatcher
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JFileChooser

class FullSettingsDialog(
  private val project: Project,
  private val files: List<VirtualFile>,
  language2files: Map<String, Set<VirtualFile>>
) : DialogWrapper(true) {
  companion object {
    const val configStateKey = "com.intellij.cce.config.full"
  }

  private val properties = PropertiesComponent.getInstance(project)
  private val dispatcher = EventDispatcher.create(SettingsListener::class.java)
  private val languageConfigurable = LanguageConfigurable(dispatcher, language2files)

  private val configurators: List<EvaluationConfigurable> = listOf(
    languageConfigurable,
    CompletionTypeConfigurable(),
    ContextConfigurable(),
    PrefixConfigurable(),
    FiltersConfigurable(dispatcher, languageConfigurable.language()),
    FilteringOnInterpretationConfigurable(),
    FlowConfigurable()
  )

  init {
    init()
    title = EvaluationPluginBundle.message("evaluation.settings.title")
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      val value = properties.getValue(configStateKey)
      val previousState = try {
        if (value == null) ConfigFactory.defaultConfig(project.basePath!!)
        else ConfigFactory.deserialize(value)
      }
      catch (e: Throwable) {
        ConfigFactory.defaultConfig(project.basePath!!)
      }
      configurators.forEach {
        row { cell(it.createPanel(previousState)).align(AlignX.FILL) }
      }
    }
  }

  override fun createLeftSideActions(): Array<Action> {
    return super.createLeftSideActions() + listOf(object : DialogWrapperAction(EvaluationPluginBundle.message("evaluation.settings.config.save")) {
      override fun doAction(e: ActionEvent?) {
        val fileChooser = JFileChooser().apply {
          fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
          dialogTitle = EvaluationPluginBundle.message("evaluation.settings.config.directory")
        }
        val result = fileChooser.showOpenDialog(contentPanel)
        if (result == JFileChooser.APPROVE_OPTION) {
          val config = buildConfig()
          ConfigFactory.save(config, fileChooser.selectedFile.toPath())
          Messages.showInfoMessage(EvaluationPluginBundle.message("evaluation.settings.config.saved.text"),
                                   EvaluationPluginBundle.message("evaluation.settings.config.saved.title"))
        }
      }
    })
  }

  fun buildConfig(): Config {
    val config = Config.build(project.basePath!!, languageConfigurable.language()) {
      configurators.forEach { it.configure(this) }
      evaluationRoots = files.map { FilesHelper.getRelativeToProjectPath(project, it.path) }.toMutableList()
    }

    properties.setValue(configStateKey, ConfigFactory.serialize(config))

    return config
  }
}