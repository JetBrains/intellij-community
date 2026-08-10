// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.ide.util.treeView.DefaultTreeModelWithCachedPresentation
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.popup.HintUpdateSupply

internal class FrontendProjectViewTree(treeModel: DefaultTreeModelWithCachedPresentation) : DnDAwareTree(treeModel) {
  init {
    isRootVisible = false
    CustomizationUtil.installPopupHandler(this, IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionPlaces.PROJECT_VIEW_POPUP)
    TreeUIHelper.getInstance().installTreeSpeedSearch(this)
    HintUpdateSupply.installDataContextHintUpdateSupply(this)
  }
}
