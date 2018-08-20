// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable

interface SshConfigurableBuilder {
  fun build(treeUpdater: Runnable): NamedConfigurable<*>
}

interface SshConfigurableProvider {
  fun getConfigurables(project: Project?): List<SshConfigurableBuilder>
}

class SshConfigurables private constructor() {
  companion object {
    val EP_NAME = ExtensionPointName.create<SshConfigurableProvider>("com.intellij.sshConfigurableProvider")
  }
}