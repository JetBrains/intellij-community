// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.javaView

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.groovy.transformations.isUnderTransformation
import org.jetbrains.plugins.groovy.util.getPackageAndShortName

class GroovyLightInnerClassFinder(project: Project) : GroovyClassFinder(project) {

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<out PsiClass> {
    val (containingClassFqn, innerClassName) = getPackageAndShortName(qualifiedName)
    return super.findClasses(containingClassFqn, scope).mapNotNull { findInnerLightClass(it, innerClassName) }.toTypedArray()
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    val (containingClassFqn, innerClassName) = getPackageAndShortName(qualifiedName)
    val containingClass = super.findClass(containingClassFqn, scope) ?: return null
    return findInnerLightClass(containingClass, innerClassName)
  }

  private fun findInnerLightClass(clazz: PsiClass, name: String): PsiClass? {
    return if (isUnderTransformation(clazz)) {
      null
    }
    else {
      clazz.findInnerClassByName(name, false) as? LightElement as? PsiClass
    }
  }
}