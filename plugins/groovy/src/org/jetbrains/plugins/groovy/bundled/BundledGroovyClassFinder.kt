// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NonClasspathClassFinder
import org.jetbrains.plugins.groovy.GroovyFileType

class BundledGroovyClassFinder(project: Project) : NonClasspathClassFinder(project, GroovyFileType.DEFAULT_EXTENSION) {

  override fun calcClassRoots(): List<VirtualFile> {
    val root = bundledGroovyJarRoot.get() ?: return emptyList()
    return listOf(root)
  }

  override fun clearCache() {
    super.clearCache()
    bundledGroovyVersion.drop()
    bundledGroovyFile.drop()
    bundledGroovyJarRoot.drop()
  }
}
