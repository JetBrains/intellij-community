// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleLocalFileDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.groovy.GreclipseSettings
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyBundle.message
import javax.swing.JComponent

class GreclipseConfigurable(val settings: GreclipseSettings) : Configurable {

  override fun getDisplayName(): @ConfigurableName String? {
    return null
  }

  val panel: DialogPanel = panel {
    group(GroovyBundle.message("configurable.greclipse.border.title")) {
      row(GroovyBundle.message("configurable.greclipse.path.label")) {
        textFieldWithBrowseButton(createSingleLocalFileDescriptor().withTitle(message("configurable.greclipse.path.chooser.description")))
          .align(AlignX.FILL)
          .bindText(settings::greclipsePath)
      }
      row(GroovyBundle.message("configurable.greclipse.command.line.params.label")) {
        val field = textField().align(AlignX.FILL)
          .bindText(settings::cmdLineParams)
          .comment(getJavaAgentCommentText(settings.cmdLineParams))
        val comment = field.comment!!
        field.onChanged {
          comment.text = getJavaAgentCommentText(it.text)
        }
      }
      row(GroovyBundle.message("configurable.greclipse.vm.options.label")) {
        textField().align(AlignX.FILL)
          .comment(GroovyBundle.message("configurable.greclipse.vm.options.comment"))
          .bindText(settings::vmOptions)
      }
      row {
        checkBox(GroovyBundle.message("configurable.greclipse.debug.checkbox"))
          .bindSelected(settings::debugInfo)
      }
    }
  }

  fun getJavaAgentCommentText(args: String): @Nls String {
    if (args.contains("-javaAgentClass")) {
      return GroovyBundle.message("configurable.greclipse.command.java.agent.class.workaround")
    }
    else {
      return ""
    }
  }

  override fun createComponent(): JComponent? {
    return panel
  }

  override fun isModified(): Boolean = panel.isModified()

  override fun apply() {
    panel.apply()
  }
}
