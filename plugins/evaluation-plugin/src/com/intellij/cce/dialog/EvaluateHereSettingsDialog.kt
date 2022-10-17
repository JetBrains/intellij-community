package com.intellij.cce.dialog

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.ConfigFactory
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EventDispatcher
import javax.swing.JComponent

class EvaluateHereSettingsDialog(
  private val project: Project,
  private val language: String,
  private val path: String
) : DialogWrapper(true) {
  companion object {
    const val configStateKey = "com.intellij.cce.config.evaluate_here"
  }

  private val dispatcher = EventDispatcher.create(SettingsListener::class.java)
  private val properties = PropertiesComponent.getInstance(project)

  private val configurators: List<EvaluationConfigurable> = listOf(
    CompletionTypeConfigurable(),
    ContextConfigurable(),
    PrefixConfigurable(),
    FiltersConfigurable(dispatcher, language),
    FilteringOnInterpretationConfigurable()
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

  fun buildConfig(): Config {
    val config = Config.build(project.basePath!!, language) {
      configurators.forEach { it.configure(this) }
      evaluationRoots = mutableListOf(path)
    }

    properties.setValue(configStateKey, ConfigFactory.serialize(config))

    return config
  }
}
