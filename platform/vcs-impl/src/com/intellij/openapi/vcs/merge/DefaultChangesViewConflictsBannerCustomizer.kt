// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangesViewNodeAction
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.platform.vcs.changes.ChangesUtil

internal class DefaultChangesViewConflictsBannerCustomizer : ChangesViewConflictsBannerCustomizer {
    override val name: String
        get() = VcsBundle.message("changes.view.conflicts.banner.default.resolve.action")

    override val icon: javax.swing.Icon?
        get() = null

    override fun createAction(changesView: ChangesListView): Runnable = Runnable {
      val project = changesView.project
      changesView.changesNodes.find { node -> ChangesUtil.isMergeConflict(node.userObject) }?.let { node ->
            for (extension in ChangesViewNodeAction.EP_NAME.getExtensions(project)) {
                extension.handleDoubleClick(node)
            }
        }
    }
}
