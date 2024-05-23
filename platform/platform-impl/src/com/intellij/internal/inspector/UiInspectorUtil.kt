// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.getDelegateChainRootAction
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ClientProperty
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.annotations.NonNls
import java.awt.Component
import javax.swing.JComponent

private val PROPERTY_KEY = Key.create<UiInspectorContextProvider>("UiInspectorContextProvider.Key")

object UiInspectorUtil {

  @JvmStatic
  fun registerProvider(component: JComponent, provider: UiInspectorContextProvider) {
    ClientProperty.put(component, PROPERTY_KEY, provider)
  }

  @JvmStatic
  fun getProvider(component: Any): UiInspectorContextProvider? {
    return when (component) {
      is UiInspectorContextProvider -> component
      is JComponent -> ClientProperty.get(component, PROPERTY_KEY)
      else -> null
    }
  }

  private fun getRawActionId(action: AnAction): String? {
    return ActionManager.getInstance().getId(action)
  }

  @JvmStatic
  fun getActionId(action: AnAction): String? {
    return getRawActionId(getDelegateChainRootAction(action))
  }

  @JvmStatic
  fun collectActionGroupInfo(prefix: @NonNls String,
                             group: ActionGroup,
                             place: String?,
                             presentationFactory: PresentationFactory?): List<PropertyBean> {
    val result = ArrayList<PropertyBean>()
    result.add(PropertyBean("$prefix Place", place, true))
    result.addAll(collectAnActionInfo(group))
    if (presentationFactory != null) {
      val groupId = getActionId(group)
      val ids = presentationFactory.actions.filter { it is ActionGroup }.mapNotNull { getActionId(it) }.toSortedSet()
      if (ids.size > 1 || ids.size == 1 && groupId == null) {
        result.add(PropertyBean("All $prefix Groups", StringUtil.join(ids, ", "), true))
      }
    }
    return result
  }

  @JvmStatic
  fun collectAnActionInfo(action: AnAction): List<PropertyBean> {
    val result = ArrayList<PropertyBean>()
    val clazz = action.javaClass
    val isGroup = action is ActionGroup
    val prefix = if (isGroup) "Group" else "Action"
    result.add(PropertyBean("$prefix ID", getActionId(action), true))
    if (clazz != DefaultActionGroup::class.java) {
      result.add(PropertyBean("$prefix Class", clazz.name, true))
    }
    val classLoader = clazz.classLoader
    if (classLoader is PluginAwareClassLoader) {
      result.add(PropertyBean("$prefix Plugin ID", classLoader.pluginId.idString, true))
    }
    var depth = 1
    var obj: Any? = action
    while (obj is ActionWithDelegate<*>) {
      val suffix = " ($depth)"
      val delegate = obj.delegate
      if (delegate is AnAction) {
        result.add(PropertyBean("$prefix Delegate Class$suffix", delegate.javaClass.name))
        result.add(PropertyBean("$prefix Delegate ID$suffix", getRawActionId(delegate)))
      }
      result.add(PropertyBean("$prefix Delegate toString$suffix", delegate))
      obj = delegate
      depth++
    }
    return result
  }

  @JvmStatic
  fun getComponentName(component: Component): String {
    var name = getClassName(component)
    val componentName = component.name
    if (StringUtil.isNotEmpty(componentName)) {
      name += " \"$componentName\""
    }
    return name
  }

  @JvmStatic
  fun getClassName(value: Any): String {
    val clazz0 = value.javaClass
    val clazz = if (clazz0.isAnonymousClass) clazz0.superclass else clazz0
    return clazz.simpleName
  }

  @JvmStatic
  fun getClassPresentation(value: Any?): String {
    if (value == null) return "[null]"
    return getClassPresentation(value.javaClass)
  }

  fun getClassPresentation(clazz0: Class<*>): String {
    val clazz = if (clazz0.isAnonymousClass) clazz0.superclass else clazz0
    val simpleName = clazz.simpleName
    return simpleName + " (" + clazz.packageName + ")"
  }

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
