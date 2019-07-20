// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

class RemoteStatusChangeNodeDecorator @JvmOverloads constructor(
  private val remoteRevisionsCache: RemoteRevisionsCache,
  private val listState: ChangeListRemoteState? = null,
  private val index: Int = -1
) : ChangeNodeDecorator {

  override fun decorate(change: Change, component: SimpleColoredComponent, isShowFlatten: Boolean) {
    val isUpToDate = remoteRevisionsCache.isUpToDate(change)

    listState?.report(index, isUpToDate)
    if (!isUpToDate) {
      component.append(" ").append(message("change.nodetitle.change.is.outdated"), SimpleTextAttributes.ERROR_ATTRIBUTES)
    }
  }

  override fun preDecorate(change: Change, renderer: ChangesBrowserNodeRenderer, showFlatten: Boolean) = Unit
}
