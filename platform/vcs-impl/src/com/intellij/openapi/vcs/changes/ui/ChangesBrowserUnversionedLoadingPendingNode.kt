// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ChangesBrowserUnversionedLoadingPendingNode : ChangesBrowserNode<ChangesBrowserNode.Tag>(UNVERSIONED_FILES_TAG) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(VcsBundle.message("changes.nodetitle.untracked.updating.delayed"),
                    SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }

  @Nls
  override fun getTextPresentation(): String = getUserObject().toString() //NON-NLS

  override fun getSortWeight(): Int = UNVERSIONED_SORT_WEIGHT
}
