// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface DefaultToolWindowLayoutProvider {
  fun createV1Layout(): List<ToolWindowDescriptor>

  fun createV2Layout(): List<ToolWindowDescriptor>
}

class DefaultToolWindowLayoutBuilder {
  var anchor = ToolWindowDescriptor.ToolWindowAnchor.LEFT
    set(value) {
      field = value
      order = 0
    }

  private var order = 0

  private val list = mutableListOf<ToolWindowDescriptor>()

  fun add(id: String, weight: Float = -1f, contentUiType: ToolWindowDescriptor.ToolWindowContentUiType? = null) {
    val descriptor = ToolWindowDescriptor(id = id)
    if (weight != -1f) {
      descriptor.weight = weight
    }
    contentUiType?.let {
      descriptor.contentUiType = it
    }
    add(descriptor)
  }

  fun add(info: ToolWindowDescriptor) {
    info.anchor = anchor
    info.order = order++
    list.add(info)
  }

  fun build(): List<ToolWindowDescriptor> = list
}

@Suppress("DuplicatedCode")
@Internal
open class IntellijPlatformDefaultToolWindowLayoutProvider : DefaultToolWindowLayoutProvider {
  final override fun createV1Layout(): List<ToolWindowDescriptor> {
    val builder = DefaultToolWindowLayoutBuilder()

    // left stripe
    builder.add("Project", weight = 0.25f, contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO)

    // right stripe
    builder.anchor = ToolWindowDescriptor.ToolWindowAnchor.RIGHT
    builder.add("Notifications", weight = 0.25f)

    // bottom stripe
    builder.anchor = ToolWindowDescriptor.ToolWindowAnchor.BOTTOM

    builder.add(id = "Version Control")
    builder.add(id = "Find")
    builder.add(id = "Run")
    builder.add(id = "Debug", weight = 0.4f)
    builder.add(id = "Inspection", weight = 0.4f)

    return builder.build()
  }

  @Suppress("DuplicatedCode")
  override fun createV2Layout(): List<ToolWindowDescriptor> {
    val builder = DefaultToolWindowLayoutBuilder()

    // left stripe
    builder.add("Project", weight = 0.25f, contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO)
    configureLeftVisibleOnLargeStripe(builder)

    // right stripe
    builder.anchor = ToolWindowDescriptor.ToolWindowAnchor.RIGHT
    builder.add("Notifications", contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO, weight = 0.25f)
    configureRightVisibleOnLargeStripe(builder)

    // bottom stripe
    builder.anchor = ToolWindowDescriptor.ToolWindowAnchor.BOTTOM
    configureBottomVisibleOnLargeStripe(builder)
    return builder.build()
  }

  open fun configureLeftVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    builder.add("Commit", weight = 0.25f)
    builder.add("Structure", weight = 0.25f)
  }

  open fun configureRightVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    builder.add("Database", weight = 0.25f)
    builder.add("Gradle", weight = 0.25f)
    builder.add("Maven", weight = 0.25f)
  }

  open fun configureBottomVisibleOnLargeStripe(builder: DefaultToolWindowLayoutBuilder) {
    builder.add("Version Control")
    // Auto-Build
    builder.add("Problems")
    // Problems
    builder.add("Problems View")
    builder.add("Terminal")
  }
}