// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.bundled

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.getQualifiedName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NonClasspathClassFinder
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.GroovyFileType

class BundledGroovyClassFinder(project: Project) : NonClasspathClassFinder(project, GroovyFileType.DEFAULT_EXTENSION) {

  override fun calcClassRoots(): List<VirtualFile> {
    val root = bundledGroovyJarRoot ?: return emptyList()
    return listOf(root)
  }

  override fun getSubPackages(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiPackage> {
    val pkgName = psiPackage.qualifiedName
    val names = getCache(scope).getSubpackageNames(pkgName, scope)
    if (names.isEmpty()) return PsiPackage.EMPTY_ARRAY
    val packages = names.map { name ->
      val subPackageFqn = getQualifiedName(pkgName, name)
      PsiPackageImpl(psiManager, subPackageFqn)
    }
    return ContainerUtil.toArray(packages, PsiPackage.EMPTY_ARRAY)
  }
}
