// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsMappingConfigurable(private val project: Project) : BoundSearchableConfigurable(
  VcsBundle.message("configurable.VcsDirectoryConfigurationPanel.display.name"),
  HELP_ID,
  "project.propVCSSupport.DirectoryMappings"
), Configurable.NoScroll {

  companion object {
    const val HELP_ID: String = "project.propVCSSupport.Mappings"
  }

  override fun createPanel(): DialogPanel {
    val result = VcsDirectoryConfigurationPanel(project)

    disposable!!.whenDisposed { Disposer.dispose(result) }

    return result.createMainComponent()
  }
}
