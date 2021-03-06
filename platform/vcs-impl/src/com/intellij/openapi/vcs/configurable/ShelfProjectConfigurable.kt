// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent

class ShelfProjectConfigurable(val project: Project) : BoundSearchableConfigurable(
  VcsBundle.message("shelf.tab"),
  "project.propVCSSupport.Shelf",
  "Shelf.Project.Settings"
) {
  override fun createPanel(): DialogPanel {
    val vcsConfig = VcsConfiguration.getInstance(project)
    val shelveManager = ShelveChangesManager.getInstance(project)

    val shelfLocation = ComponentPanelBuilder.createCommentComponent(createPresentableShelfLocation(), true, -1, false)
    return panel {
      row {
        checkBox(VcsBundle.message("shelve.remove.successfully.applied.files.checkbox"),
                 { shelveManager.isRemoveFilesFromShelf }, { shelveManager.isRemoveFilesFromShelf = it })
          .applyToComponent { mnemonic = KeyEvent.VK_R }
      }
      row {
        checkBox(VcsBundle.message("vcs.shelf.store.base.content"), vcsConfig::INCLUDE_TEXT_INTO_SHELF,
                 VcsBundle.message("settings.shelf.content.larger", VcsConfiguration.ourMaximumFileForBaseRevisionSize / 1000))
          .applyToComponent { setMnemonic('b') }
      }
      row {
        cell(isFullWidth = true) {
          button(VcsBundle.message("settings.change.shelves.location")) {
            if (ShelfStorageConfigurationDialog(project).showAndGet()) {
              shelfLocation.text = createPresentableShelfLocation()
            }
          }.enabled(!project.isDefault)
          shelfLocation().withLargeLeftGap()
        }
      }
    }
  }

  @Nls
  private fun createPresentableShelfLocation(): String {
    val prefix = if (project.isDefault) {
      VcsBundle.message("settings.default.location")
    }
    else {
      VcsBundle.message("settings.current.location")
    }

    val vcsConfig = VcsConfiguration.getInstance(project)
    val shelfPath = if (vcsConfig.USE_CUSTOM_SHELF_PATH) {
      FileUtil.toSystemDependentName(vcsConfig.CUSTOM_SHELF_PATH.orEmpty())
    }
    else {
      getDefaultShelfPresentationPath(project)
    }

    return prefix + StringUtil.escapeXmlEntities(shelfPath)
  }

  companion object {
    @Nls
    @JvmStatic
    fun getDefaultShelfPresentationPath(project: Project): String {
      return if (project.isDefault) ShelveChangesManager.DEFAULT_PROJECT_PRESENTATION_PATH
      else ShelveChangesManager.getDefaultShelfPath(project).toString()
    }
  }
}