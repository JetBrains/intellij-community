// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyProjectWizardUtils")
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import java.awt.Component
import javax.swing.ComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingUtilities

const val GROOVY_SDK_FALLBACK_VERSION = "3.0.9"

private const val MAIN_FILE = "Main.groovy"
private const val MAIN_GROOVY_TEMPLATE = "template.groovy"

fun Row.groovySdkComboBox(property: ObservableMutableProperty<String?>) {
  comboBox(getInitializedModel(), fallbackAwareRenderer)
    .columns(COLUMNS_MEDIUM)
    .bindItem(property)
    .validationOnInput {
      if (property.get() == null) {
        warning(GroovyBundle.message("new.project.wizard.groovy.retrieving.has.failed"))
      }
      else {
        null
      }
    }
}

fun Panel.addSampleCodeCheckbox(property : GraphProperty<Boolean>) {
  row {
    checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      .bindSelected(property)
  }.topGap(TopGap.SMALL)
}


fun ModuleBuilder.createSampleGroovyCodeFile(project: Project, sourceDirectory: VirtualFile) {
  WriteCommandAction.runWriteCommandAction(project, GroovyBundle.message("new.project.wizard.groovy.creating.main.file"), null,
     Runnable {
       val fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(MAIN_GROOVY_TEMPLATE)
       val helloWorldFile = sourceDirectory.createChildData(this, MAIN_FILE)
       VfsUtil.saveText(helloWorldFile, fileTemplate.text)
     })
}


private val fallbackAwareRenderer: DefaultListCellRenderer = object : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
    val representation = value as? String ?: GROOVY_SDK_FALLBACK_VERSION // NON-NLS
    return super.getListCellRendererComponent(list, representation, index, isSelected, cellHasFocus)
  }
}

private fun getInitializedModel(): ComboBoxModel<String?> {
  val model = CollectionComboBoxModel<String?>()
  loadLatestGroovyVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
    override fun onSuccess(versions: MutableList<out FrameworkLibraryVersion>) {
      SwingUtilities.invokeLater {
        for (version in versions.sortedWith(::moveUnstableVersionToTheEnd)) {
          model.add(version.versionString)
        }
        model.selectedItem = model.items.first()
      }
    }

    override fun onError(errorMessage: String) {
      model.add(null)
      model.selectedItem = model.items.first()
    }
  })
  return model
}


internal fun moveUnstableVersionToTheEnd(left: FrameworkLibraryVersion, right: FrameworkLibraryVersion): Int {
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