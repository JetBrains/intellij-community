// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object UiInspectorImpl {
  @JvmStatic
  fun openClassByFqn(project: Project?, jvmFqn: String, requestFocus: Boolean) {
    val classElement = findClassByFqn(project, jvmFqn) ?: return
    val element = classElement.navigationElement
    if (element is Navigatable) {
      element.navigate(requestFocus)
    }
    else {
      PsiNavigateUtil.navigate(classElement, requestFocus)
    }
  }

  @JvmStatic
  fun findClassByFqn(project: Project?, jvmFqn: String): PsiElement? {
    if (project == null) return null
    try {
      val javaPsiFacadeFqn = "com.intellij.psi.JavaPsiFacade"
      val pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(javaPsiFacadeFqn)
      val facade = if (pluginId != null) {
        val plugin = PluginManager.getInstance().findEnabledPlugin(pluginId)
        if (plugin != null) {
          Class.forName(javaPsiFacadeFqn, false, plugin.pluginClassLoader)
        }
        else {
          null
        }
      }
      else {
        Class.forName(javaPsiFacadeFqn)
      }
      if (facade == null) return null
      val getInstance = facade.getDeclaredMethod("getInstance", Project::class.java)
      val findClass = facade.getDeclaredMethod("findClass", String::class.java, GlobalSearchScope::class.java)
      val ourFqn = jvmFqn.replace('$', '.')
      val result = findClass.invoke(getInstance.invoke(null, project), ourFqn, GlobalSearchScope.allScope(project)) ?: run {
        // if provided jvmFqn is an anonymous class, try to find a containing class and then find anonymous class inside
        val parts = jvmFqn.split("\\$\\d+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val containingClassJvmFqn = parts[0]
        val containingClassOurFqn = containingClassJvmFqn.replace('$', '.')
        val containingClass = findClass.invoke(getInstance.invoke(null, project),
                                               containingClassOurFqn, GlobalSearchScope.allScope(project))
        if (containingClass is PsiElement) {
          findAnonymousClass(containingClass, jvmFqn) ?: containingClass
        }
        else {
          null
        }
      }
      return result as? PsiElement
    }
    catch (_: Exception) {
    }
    return null
  }

  private fun findAnonymousClass(containingClass: PsiElement, jvmFqn: String): PsiElement? {
    try {
      val clazz = Class.forName("com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor")
      val method = clazz.getDeclaredMethod("pathToAnonymousClass", String::class.java)
      method.isAccessible = true
      val path = method.invoke(null, jvmFqn) as? String ?: return null
      val getElement = clazz.getDeclaredMethod("getElement", PsiElement::class.java, String::class.java)
      return getElement.invoke(null, containingClass, path) as PsiElement
    }
    catch (_: Exception) {
    }
    return null
  }
}
