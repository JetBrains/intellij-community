// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.idea.AppMode
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.ApiStatus
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.*
import javax.swing.JComponent

object RemoteTransferUIManager {
  private val becontrolizationForbidKey = Key.create<Boolean>("lux.localComponents.forbidBeControlization")
  private val wellBeControlizableKey = Key.create<Boolean>("lux.localComponents.wellBeControlizable")
  private val paintedQuickly = Key.create<Boolean>("lux.localComponents.paintedQuickly")
  private val forceDirectTransferKey = Key.create<Boolean>("lux.force.direct.transfer")
  private val focusInlayOnShowKey = Key.create<Boolean>("lux.block.inlay.focus.on.show")
  private val becontrolizaitionExceptionKey = "lux.localComponents.becontrolizationException"

  private fun _forbidBeControlizationInLux(comp: Any, registryKey: String?) {
    try {
      if (registryKey != null && !Registry.stringValue(becontrolizaitionExceptionKey).contains(registryKey))
        return
    } catch (ignore: MissingResourceException) {
      return
    }
    when (comp) {
      is UserDataHolder -> comp.putUserData(becontrolizationForbidKey, true)
      is JComponent -> comp.putUserData(becontrolizationForbidKey, true)
      else -> error("Wrong usage")
    }
  }

  @JvmStatic
  fun forbidBeControlizationInLux(comp: UserDataHolder, registryKey: String?) {
    _forbidBeControlizationInLux(comp, registryKey)
  }

  @JvmStatic
  fun forbidBeControlizationInLux(comp: JComponent, registryKey: String?) {
    _forbidBeControlizationInLux(comp, registryKey)
  }

  @JvmStatic
  fun setWellBeControlizable(component: UserDataHolder) {
    component.putUserData(wellBeControlizableKey, true)
  }

  @JvmStatic
  fun setWellBeControlizable(component: JComponent) {
    component.putUserData(wellBeControlizableKey, true)
  }

  @JvmStatic
  fun setWellBeControlizableAndPaintedQuickly(component: JComponent) {
    component.putUserData(wellBeControlizableKey, true)
    component.putUserData(paintedQuickly, true)
  }

  @JvmStatic
  fun isBeControlizationForbiddenInLux(component: JComponent): Boolean {
    return component.getUserData(becontrolizationForbidKey) == true
  }

  @JvmStatic
  fun isBeControlizationForbiddenInLux(component: UserDataHolder): Boolean {
    return component.getUserData(becontrolizationForbidKey) == true
  }

  @JvmStatic
  fun isPaintedQuickly(component: JComponent): Boolean {
    return component.getUserData(paintedQuickly) == true
  }

  @JvmStatic
  fun isWellBeControlizable(component: JComponent): Boolean {
    return component.getUserData(wellBeControlizableKey) == true
  }

  @JvmStatic
  fun isWellBeControlizable(component: UserDataHolder): Boolean {
    return component.getUserData(wellBeControlizableKey) == true
  }

  @JvmStatic
  fun forceDirectTransfer(component: JComponent) {
    if (component is UserDataHolder) {
      component.putUserData(forceDirectTransferKey, true)
    } else {
      component.putUserData(forceDirectTransferKey, true)
    }
  }

  @JvmStatic
  fun isForceDirectTransfer(session: ClientAppSession, component: JComponent): Boolean {
    if (!session.isController) return false
    return if (component is UserDataHolder)
      component.getUserData(forceDirectTransferKey) == true
    else
      component.getUserData(forceDirectTransferKey) == true
  }

  @JvmStatic
  fun focusWhenShown(project: Project, component: JComponent) {
    if (!AppMode.isRemoteDevHost()) {
      return
    }

    component.addHierarchyListener(object : HierarchyListener {
      override fun hierarchyChanged(e: HierarchyEvent?) {
        if (!component.isShowing) return
        IdeFocusManager.getInstance(project).requestFocus(component, false)
        component.removeHierarchyListener(this)
      }
    })
  }

  @JvmStatic
  fun markFocusOnShowForInlay(component: JComponent) {
    component.putUserData(focusInlayOnShowKey, true)
  }

  @JvmStatic
  fun isFocusWhenShown(inlay: Inlay<*>): Boolean {
    val component = (inlay.renderer as? ComponentInlayRenderer<*>)?.component as? JComponent ?: return false
    return component.getUserData(focusInlayOnShowKey) == true
  }
}

@ApiStatus.Internal
object RemoteUIDataConstants {
  const val BE_CONTROLS_SKIP = "BE_CONTROLS_SKIP"
}