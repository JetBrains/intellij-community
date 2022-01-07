// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GroovyProjectWizardUtils")
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.framework.library.FrameworkLibraryVersion
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.orNull
import com.intellij.util.download.DownloadableFileSetVersions
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.loadLatestGroovyVersions
import java.awt.Component
import java.util.*
import javax.swing.ComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.SwingUtilities

const val GROOVY_SDK_FALLBACK_VERSION = "3.0.9"

fun Row.groovySdkComboBox(property : GraphProperty<Optional<String>>) {
  comboBox(getInitializedModel(), fallbackAwareRenderer)
    .columns(COLUMNS_MEDIUM)
    .bindItem(property)
    .validationOnInput {
      if (property.get().isEmpty) {
        warning(GroovyBundle.message("new.project.wizard.groovy.retrieving.has.failed"))
      }
      else {
        null
      }
    }
}

private val fallbackAwareRenderer: DefaultListCellRenderer = object : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
    val representation = value.castSafelyTo<Optional<*>>()?.orNull()?.castSafelyTo<String>() ?: GROOVY_SDK_FALLBACK_VERSION // NON-NLS
    return super.getListCellRendererComponent(list, representation, index, isSelected, cellHasFocus)
  }
}

private fun getInitializedModel(): ComboBoxModel<Optional<String>> {
  val model = CollectionComboBoxModel<Optional<String>>()
  loadLatestGroovyVersions(object : DownloadableFileSetVersions.FileSetVersionsCallback<FrameworkLibraryVersion>() {
    override fun onSuccess(versions: MutableList<out FrameworkLibraryVersion>) {
      SwingUtilities.invokeLater {
        for (version in versions.sortedWith(::moveUnstableVersionToTheEnd)) {
          model.add(Optional.of(version.versionString))
        }
        model.selectedItem = model.items.first()
      }
    }

    override fun onError(errorMessage: String) {
      model.add(Optional.empty())
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