// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.platform.projectView.pane.ProjectViewNodeModelImpl
import com.intellij.ui.ClientProperty
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.popup.HintUpdateSupply
import com.intellij.ui.tabs.FileColorManagerImpl
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Color

internal class FrontendProjectViewTree(treeModel: DefaultTreeModelWithCachedPresentation) : DnDAwareTree(treeModel) {
  init {
    isRootVisible = false
    CustomizationUtil.installPopupHandler(this, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP)
    TreeUIHelper.getInstance().installTreeSpeedSearch(this)
    HintUpdateSupply.installDataContextHintUpdateSupply(this)
    ClientProperty.put(this, DefaultTreeUI.AUTO_EXPAND_ALLOWED, true)
  }

  override fun isFileColorsEnabled(): Boolean {
    val enabled = FileColorManagerImpl._isEnabled() && FileColorManagerImpl._isEnabledForProjectView()
    val opaque: Boolean = isOpaque
    if (enabled && opaque) {
      isOpaque = false
    }
    else if (!enabled && !opaque) {
      isOpaque = true
    }
    return enabled
  }

  override fun getFileColorFor(node: Any?): Color? {
    val userObject = TreeUtil.getUserObject(node) ?: return null
    if (userObject is ProjectViewNodeModelImpl<*>) {
      return userObject.presentation.background
    }
    return null
  }
}
