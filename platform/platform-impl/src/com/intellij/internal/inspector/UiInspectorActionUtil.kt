// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.getDelegateChainRootAction
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls

object UiInspectorActionUtil {
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
}
