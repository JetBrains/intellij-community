// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.VcsLogActionIds

private class VcsLogCustomizableActionGroupProvider : CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
    registrar.addCustomizableActionGroup(VcsLogActionIds.TOOLBAR_RIGHT_CORNER_ACTION_GROUP,
                                         VcsLogBundle.message("vcs.log.right.corner.toolbar"))
    registrar.addCustomizableActionGroup(VcsLogActionIds.CHANGES_BROWSER_TOOLBAR_ACTION_GROUP,
                                         VcsLogBundle.message("vcs.log.changes.browser.toolbar"))
    registrar.addCustomizableActionGroup(VcsLogActionIds.FILE_HISTORY_TOOLBAR_ACTION_GROUP,
                                         VcsLogBundle.message("file.history.toolbar"))
  }
}