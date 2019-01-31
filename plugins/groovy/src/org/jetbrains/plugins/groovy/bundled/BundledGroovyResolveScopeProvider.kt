// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.module.impl.scopes.JdkScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeProvider
import com.intellij.psi.search.GlobalSearchScope

class BundledGroovyResolveScopeProvider : ResolveScopeProvider() {

  override fun getResolveScope(file: VirtualFile, project: Project): GlobalSearchScope? {
    val index = ProjectFileIndex.SERVICE.getInstance(project)
    index.getModuleForFile(file)?.let { return null }

    val rootFile = VfsUtil.getRootFile(file)
    if (rootFile == bundledGroovyJarRoot) {
      val scope = createBundledGroovyScope(project) ?: return null
      val sdk = ProjectRootManager.getInstance(project).projectSdk ?: return null
      val jdkScope = JdkScope(project, sdk.rootProvider.getFiles(OrderRootType.CLASSES), sdk.rootProvider.getFiles(OrderRootType.SOURCES), sdk.name)

     return scope.uniteWith(jdkScope)
    }
    return null
  }
}