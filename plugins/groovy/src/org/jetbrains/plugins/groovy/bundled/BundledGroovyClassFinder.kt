// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NonClasspathClassFinder
import org.jetbrains.plugins.groovy.GroovyFileType

class BundledGroovyClassFinder(project: Project) : NonClasspathClassFinder(project, GroovyFileType.DEFAULT_EXTENSION) {

  override fun calcClassRoots(): List<VirtualFile> {
    val root = bundledGroovyJarRoot ?: return emptyList()
    return listOf(root)
  }
}
