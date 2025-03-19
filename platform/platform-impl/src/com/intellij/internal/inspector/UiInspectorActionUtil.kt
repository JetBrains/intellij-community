// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.getDelegateChainRootAction
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.TreeSet

@ApiStatus.Internal
object UiInspectorActionUtil {
  @JvmStatic
  fun getActionId(action: AnAction): String? {
    return ActionManager.getInstance().getId(getDelegateChainRootAction(action))
  }

  @JvmStatic
  fun collectActionGroupInfo(
    prefix: @NonNls String,
    group: ActionGroup,
    place: String?,
    presentationFactory: PresentationFactory?,
  ): List<PropertyBean> {
    val result = ArrayList<PropertyBean>()
    result.add(PropertyBean("$prefix Place", place, true))
    result.addAll(collectAnActionInfo(group))
    if (presentationFactory != null) {
      val groupId = getActionId(group)
      val ids = presentationFactory.actions.asSequence().filter { it is ActionGroup }.mapNotNullTo(TreeSet()) { getActionId(it) }
      if (ids.size > 1 || ids.size == 1 && groupId == null) {
        result.add(PropertyBean("All $prefix Groups", ids.joinToString(", "), true))
      }
    }
    return result
  }

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
    val actionManager = ActionManager.getInstance()
    var depth = 1
    var obj: Any? = action
    while (obj is ActionWithDelegate<*>) {
      val suffix = " ($depth)"
      val delegate = obj.delegate
      if (delegate is AnAction) {
        result.add(PropertyBean("$prefix Delegate Class$suffix", delegate.javaClass.name))
        result.add(PropertyBean("$prefix Delegate ID$suffix", actionManager.getId(delegate)))
      }
      result.add(PropertyBean("$prefix Delegate toString$suffix", delegate))
      obj = delegate
      depth++
    }
    return result
  }
}