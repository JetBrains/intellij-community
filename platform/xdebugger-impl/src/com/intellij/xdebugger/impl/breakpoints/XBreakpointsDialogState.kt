// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection
import gnu.trove.THashSet

@Tag("breakpoints-dialog")
class XBreakpointsDialogState : BaseState() {
  @get:XCollection(propertyElementName = "selected-grouping-rules", elementName = "grouping-rule", valueAttributeName = "id")
  var selectedGroupingRules by property(THashSet<String>())

  @get:Transient // Not saved for now
  var treeState: TreeState? = null
}
