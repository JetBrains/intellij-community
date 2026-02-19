// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xdebugger.impl.pinned.items.PinnedItemInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("pin-to-top-manager")
class PinToTopManagerState : BaseState() {
  @get:XCollection(propertyElementName = "pinned-members")
  var pinnedMembersList by list<PinnedItemInfo>()
}