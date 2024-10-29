// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Tag("unified_weights")
@Property(style = Property.Style.ATTRIBUTE)
class UnifiedToolWindowWeights : BaseState() {

  companion object {
    const val TAG: String = "unified_weights"
  }

  @get:Attribute("top") var top: Float by property(WindowInfoImpl.DEFAULT_WEIGHT)
  @get:Attribute("left") var left: Float by property(WindowInfoImpl.DEFAULT_WEIGHT)
  @get:Attribute("bottom") var bottom: Float by property(WindowInfoImpl.DEFAULT_WEIGHT)
  @get:Attribute("right") var right: Float by property(WindowInfoImpl.DEFAULT_WEIGHT)

  operator fun get(anchor: ToolWindowAnchor): Float = when (anchor) {
    ToolWindowAnchor.TOP -> top
    ToolWindowAnchor.LEFT -> left
    ToolWindowAnchor.BOTTOM -> bottom
    ToolWindowAnchor.RIGHT -> right
    else -> throw IllegalArgumentException("anchor=$anchor")
  }

  operator fun set(anchor: ToolWindowAnchor, weight: Float) {
    when (anchor) {
      ToolWindowAnchor.TOP -> top = weight
      ToolWindowAnchor.LEFT -> left = weight
      ToolWindowAnchor.BOTTOM -> bottom = weight
      ToolWindowAnchor.RIGHT -> right = weight
      else -> throw IllegalArgumentException("anchor=$anchor")
    }
  }

  fun copy(): UnifiedToolWindowWeights = UnifiedToolWindowWeights().also { that ->
    that.top = this.top
    that.left = this.left
    that.bottom = this.bottom
    that.right = this.right
  }

}
