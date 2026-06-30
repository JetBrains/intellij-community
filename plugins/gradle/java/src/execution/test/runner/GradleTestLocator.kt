// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.Location
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

object GradleTestLocator : SMTestLocator, DumbAware {

  override fun getLocation(
    protocol: String,
    path: String,
    project: Project,
    scope: GlobalSearchScope
  ): List<Location<*>> {
    return getLocation(protocol, path, null, project, scope)
  }

  override fun getLocation(
    protocol: String,
    path: String,
    metainfo: String?,
    project: Project,
    scope: GlobalSearchScope
  ): List<Location<*>> {
    val locations = JavaTestLocator.INSTANCE.getLocation(protocol, path, metainfo, project, scope)
    if (locations.isNotEmpty()) {
      return locations
    }
    return findExtensionLocations { it.getLocation(protocol, path, metainfo, project, scope) }
  }

  override fun getLocation(
    stacktraceLine: String,
    project: Project,
    scope: GlobalSearchScope
  ): List<Location<*>> {
    val locations = JavaTestLocator.INSTANCE.getLocation(stacktraceLine, project, scope)
    if (locations.isNotEmpty()) {
      return locations
    }
    return findExtensionLocations { it.getLocation(stacktraceLine, project, scope) }
  }

  private fun findExtensionLocations(locator: (GradleTestLocatorExtension) -> List<Location<*>>): List<Location<*>> {
    for (extension in GradleTestLocatorExtension.EP_NAME.extensionList) {
      val locations = locator(extension)
      if (locations.isNotEmpty()) {
        return locations
      }
    }
    return emptyList()
  }
}
