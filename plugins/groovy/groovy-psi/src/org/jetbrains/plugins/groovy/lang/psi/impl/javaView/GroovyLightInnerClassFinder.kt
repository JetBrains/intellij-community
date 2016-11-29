/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.javaView

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope

class GroovyLightInnerClassFinder(project: Project) : GroovyClassFinder(project) {

  override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<out PsiClass> {
    val containingClassFqn = StringUtil.getPackageName(qualifiedName)
    val innerClassName = StringUtil.getShortName(qualifiedName)
    return super.findClasses(containingClassFqn, scope).mapNotNull { findInnerLightClass(it, innerClassName) }.toTypedArray()
  }

  override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
    val containingClassFqn = StringUtil.getPackageName(qualifiedName)
    val containingClass = super.findClass(containingClassFqn, scope) ?: return null
    val innerClassName = StringUtil.getShortName(qualifiedName)
    return findInnerLightClass(containingClass, innerClassName)
  }

  fun findInnerLightClass(clazz: PsiClass, name: String): PsiClass? {
    return clazz.findInnerClassByName(name, false) as? LightElement as?PsiClass
  }
}