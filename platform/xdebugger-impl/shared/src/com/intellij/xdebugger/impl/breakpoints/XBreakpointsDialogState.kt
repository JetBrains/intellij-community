// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("breakpoints-dialog")
class XBreakpointsDialogState : BaseState() {
  @get:XCollection(propertyElementName = "selected-grouping-rules", elementName = "grouping-rule", valueAttributeName = "id")
  var selectedGroupingRules by stringSet()

  // not saved for now
  @get:Transient
  var treeState: TreeState? = null
}