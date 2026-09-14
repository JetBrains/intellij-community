// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.vcs

import com.intellij.execution.JUnitBundle
import com.intellij.execution.junit2.configuration.JUnitTestDiscoveryProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import org.jetbrains.annotations.Nls

internal class JUnitTestDiscoveryProviderImpl : JUnitTestDiscoveryProvider {
  override fun getChangeListNames(project: Project): List<@Nls String> = buildList {
    add(JUnitBundle.message("test.discovery.by.all.changes.combo.item"))

    if (!project.isDefault) {
      for (changeList in ChangeListManager.getInstance(project).changeLists) {
        add(changeList.name)
      }
    }
  }
}
