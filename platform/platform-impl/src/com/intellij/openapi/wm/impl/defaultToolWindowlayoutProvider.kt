// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowContentUiType
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface DefaultToolWindowLayoutProvider {
  fun createDefaultToolWindowLayout(): List<WindowInfoImpl>
}

class DefaultToolWindowLayoutInfoBuilder {
  var anchor = ToolWindowAnchor.LEFT
    set(value) {
      field = value
      order = 0
    }

  private var order = 0

  private val list = mutableListOf<WindowInfoImpl>()

  fun add(id: String, weight: Float = -1f, contentUiType: ToolWindowContentUiType? = null) {
    val info = WindowInfoImpl()
    info.id = id
    if (weight != -1f) {
      info.weight = weight
    }
    contentUiType?.let {
      info.contentUiType = it
    }

    add(info)
  }

  fun add(info: WindowInfoImpl) {
    info.anchor = anchor
    info.largeStripeAnchor = anchor
    info.order = order++
    info.orderOnLargeStripe = info.order
    info.isFromPersistentSettings = false
    info.resetModificationCount()
    list.add(info)
  }

  fun build(): List<WindowInfoImpl> = list
}

@Internal
open class IntellijPlatformDefaultToolWindowLayoutProvider : DefaultToolWindowLayoutProvider {
  protected fun DefaultToolWindowLayoutInfoBuilder.visibleOnLargeStripe(id: String, weight: Float = 0.25f) {
    add(WindowInfoImpl().apply {
      this.id = id
      this.weight = weight
      isVisibleOnLargeStripe = true
    })
  }

  @Suppress("DuplicatedCode")
  final override fun createDefaultToolWindowLayout(): List<WindowInfoImpl> {
    val builder = DefaultToolWindowLayoutInfoBuilder()

    fun combo(id: String) {
      builder.add(WindowInfoImpl().apply {
        this.id = id
        weight = 0.25f
        isVisibleOnLargeStripe = true
        contentUiType = ToolWindowContentUiType.COMBO
      })
    }

    // left stripe
    combo("Project")
    configureLeftVisibleOnLargeStripe(builder)

    // right stripe
    builder.anchor = ToolWindowAnchor.RIGHT
    builder.add("Notifications", contentUiType = ToolWindowContentUiType.COMBO, weight = 0.25f)
    configureRightVisibleOnLargeStripe(builder)

    // bottom stripe
    builder.anchor = ToolWindowAnchor.BOTTOM
    configureBottomVisibleOnLargeStripe(builder)

    builder.add(id = "Find")
    builder.add(id = "Run")
    builder.add(id = "Debug", weight = 0.4f)
    builder.add(id = "Inspection", weight = 0.4f)

    return builder.build()
  }

  open fun configureLeftVisibleOnLargeStripe(builder: DefaultToolWindowLayoutInfoBuilder) {
    builder.visibleOnLargeStripe("Commit")
    builder.visibleOnLargeStripe("Structure")
  }

  open fun configureRightVisibleOnLargeStripe(builder: DefaultToolWindowLayoutInfoBuilder) {
    builder.visibleOnLargeStripe("Database")
    builder.visibleOnLargeStripe("Gradle")
    builder.visibleOnLargeStripe("Maven")
  }

  open fun configureBottomVisibleOnLargeStripe(builder: DefaultToolWindowLayoutInfoBuilder) {
    builder.visibleOnLargeStripe(id = "Version Control")
    builder.visibleOnLargeStripe(id = "Problems View")
    builder.visibleOnLargeStripe(id = "Terminal")
  }
}