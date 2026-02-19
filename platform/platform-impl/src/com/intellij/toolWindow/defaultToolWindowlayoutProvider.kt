// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.impl.WindowInfoImpl
import org.jetbrains.annotations.ApiStatus.Internal

// New (extension-based) API

/**
 * An extension that defines the default tool window layout.
 *
 * Used when there is no saved tool window layout to be used as default,
 * or when the Restore Default Layout action is performed (to be implemented yet).
 *
 * The tool window layout is built by creating a builder and passing it to all
 * registered extensions. Every extension is free to modify the layout in any way
 * using the provided builder API.
 *
 * @see DefaultToolWindowLayoutBuilder
 */
interface DefaultToolWindowLayoutExtension {
  companion object {
    val EP_NAME: ExtensionPointName<DefaultToolWindowLayoutExtension> = ExtensionPointName.create("com.intellij.defaultToolWindowLayout")
  }

  fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder)
  fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder)
}

/**
 * A tool window layout builder.
 *
 * Extensions implementing [DefaultToolWindowLayoutExtension] are passed
 * an instance implementing this interface. Tool windows may be added or
 * modified using the provided [DefaultToolWindowStripeBuilder] instances,
 * and removed using the [removeAll] function.
 */
interface DefaultToolWindowLayoutBuilder {
  val left: DefaultToolWindowStripeBuilder
  val right: DefaultToolWindowStripeBuilder
  val bottom: DefaultToolWindowStripeBuilder

  /**
   * Removes all tool windows matching the given predicate.
   *
   * If no predicate is provided, remove all tool windows.
   *
   * Typical use cases:
   * - remove everything provided by the platform (no predicate) and build your own layout from scratch;
   * - clear a whole stripe (`it.anchor == WHATEVER`) and rebuild that anchor from scratch;
   * - clear everything but a few select windows (`it.id != "Project" && it.id != "Notifications"`)
   * to keep just those windows before adding your own.
   */
  fun removeAll(predicate: ((DefaultToolWindowDescriptorBuilder) -> Boolean)? = null)
}

/**
 * A tool window layout builder for a specific stripe (anchor).
 *
 * Obtained via one of the properties of the [DefaultToolWindowLayoutBuilder] interface,
 * corresponding to each docking area.
 */
interface DefaultToolWindowStripeBuilder {
  /**
   * Adds a new tool window or updates the existing one.
   *
   * If no window with a given id exists, it's created and placed onto this stripe.
   * If it exists on another stripe, it's moved onto this one.
   * In either case, an instance of [DefaultToolWindowDescriptorBuilder] corresponding
   * to the given ID is passed to the function provided in the [buildToolWindow] parameter
   * unless it's `null`. This function is free to modify the given builder in any way.
   * If several extensions try to modify the same window, the next one will see the builder
   * exactly in the state it was left in by the previous extension, so the changes are cumulative.
   */
  fun addOrUpdate(id: String, buildToolWindow: (DefaultToolWindowDescriptorBuilder.() -> Unit)? = null)

  /**
   * Adds default tool windows for the old (classic) UI onto this stripe.
   *
   * There's typically no need to call this function, as by the time the product's extension
   * is invoked, the platform has already placed all default tool windows onto the stripe.
   *
   * However, sometimes a product may want to clear everything and then restore some
   * stripes to their default state. Or use the old UI defaults for the new UI.
   */
  fun addPlatformDefaultsV1()

  /**
   * Adds default tool windows for the new (modern) UI onto this stripe.
   *
   * There's typically no need to call this function, as by the time the product's extension
   * is invoked, the platform has already placed all default tool windows onto the stripe.
   *
   * However, sometimes a product may want to clear everything and then restore some
   * stripes to their default state..
   */
  fun addPlatformDefaultsV2()
}

/**
 * A builder for a specific tool window in the default layout.
 *
 * Its properties correspond to those of [ToolWindowDescriptor].
 */
interface DefaultToolWindowDescriptorBuilder {
  /**
   * The tool window ID, as passed to [DefaultToolWindowStripeBuilder.addOrUpdate].
   *
   * Never changes.
   */
  val id: String

  /**
   * The stripe the tool window will be placed onto.
   *
   * May change if the tool window first placed on one stripe and then moved onto another
   * by invoking [DefaultToolWindowStripeBuilder.addOrUpdate] for another stripe.
   */
  val anchor: ToolWindowDescriptor.ToolWindowAnchor

  /**
   * Defines whether the tool window is initially visible, false by default.
   */
  var isVisible: Boolean

  /**
   * Defines what percentage of the frame the tool window occupies (from 0.0 to 1.0, default 0.33).
   */
  var weight: Float

  /**
   * Only applies to some tool windows, defines whether their tabs are presented as tabs or as a combobox.
   */
  var contentUiType: ToolWindowDescriptor.ToolWindowContentUiType

  /**
   * Allows two tool windows to share the same docking area.
   *
   * To use it properly, place two tool windows onto the same stripe, one with this property
   * set to `false`, another with `true`. Set [sideWeight] properties accordingly, so they add up
   * to 1.0 (default 0.5/0.5).
   */
  var isSplit: Boolean

  /**
   * Defines what percentage of the splitter the tool window occupies.
   *
   * If [isSplit] is not used on any tool window in a stripe, then this property is ignored.
   * Otherwise, it allows tool windows to split two tool windows in some proportion.
   * Defaults to 0.5.
   */
  var sideWeight: Float
}

// Old (server-based API)

@Internal
@Deprecated(
  "Used as a service and therefore doesn't work with multiple providers",
  replaceWith = ReplaceWith("DefaultToolWindowLayoutExtension")
)
interface DefaultToolWindowLayoutProvider {
  fun createV1Layout(): List<ToolWindowDescriptor>

  fun createV2Layout(): List<ToolWindowDescriptor>
}

// Implementation (new API)

internal class DefaultToolWindowLayoutPlatformExtension : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
    builder.left.addPlatformDefaultsV1()
    builder.right.addPlatformDefaultsV1()
    builder.bottom.addPlatformDefaultsV1()
  }

  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    builder.left.addPlatformDefaultsV2()
    builder.right.addPlatformDefaultsV2()
    builder.bottom.addPlatformDefaultsV2()
  }
}

internal class DefaultToolWindowLayoutBuilderImpl : DefaultToolWindowLayoutBuilder {
  private val toolWindowBuilders = LinkedHashMap<String, DefaultToolWindowDescriptorBuilderImpl>()
  override val left: DefaultToolWindowStripeBuilder = DefaultToolWindowStripeBuilderImpl(ToolWindowDescriptor.ToolWindowAnchor.LEFT)
  override val right: DefaultToolWindowStripeBuilder = DefaultToolWindowStripeBuilderImpl(ToolWindowDescriptor.ToolWindowAnchor.RIGHT)
  override val bottom: DefaultToolWindowStripeBuilder = DefaultToolWindowStripeBuilderImpl(ToolWindowDescriptor.ToolWindowAnchor.BOTTOM)

  override fun removeAll(predicate: ((DefaultToolWindowDescriptorBuilder) -> Boolean)?) {
    if (predicate == null) {
      toolWindowBuilders.clear()
    }
    else {
      toolWindowBuilders.values.removeAll(predicate)
    }
  }

  fun build(): List<ToolWindowDescriptor> {
    val result = mutableListOf<ToolWindowDescriptor>()
    add(result, left as DefaultToolWindowStripeBuilderImpl)
    add(result, right as DefaultToolWindowStripeBuilderImpl)
    add(result, bottom as DefaultToolWindowStripeBuilderImpl)
    return result
  }

  private fun add(
    result: MutableList<ToolWindowDescriptor>,
    stripe: DefaultToolWindowStripeBuilderImpl
  ) {
    for ((order, window) in stripe.build().withIndex()) {
      window.order = order
      result += window
    }
  }

  private inner class DefaultToolWindowStripeBuilderImpl(
    val anchor: ToolWindowDescriptor.ToolWindowAnchor
  ) : DefaultToolWindowStripeBuilder {

    override fun addOrUpdate(id: String, buildToolWindow: (DefaultToolWindowDescriptorBuilder.() -> Unit)?) {
      val toolWindowBuilder = toolWindowBuilders.getOrPut(id) { DefaultToolWindowDescriptorBuilderImpl(id) }
      toolWindowBuilder.anchor = anchor // having this outside of the constructor allows moving from anchor to anchor
      if (buildToolWindow != null) {
        toolWindowBuilder.buildToolWindow()
      }
    }

    override fun addPlatformDefaultsV1() {
      when (anchor) {
        ToolWindowDescriptor.ToolWindowAnchor.TOP -> throw UnsupportedOperationException("The top stripe is not supported")
        ToolWindowDescriptor.ToolWindowAnchor.LEFT -> {
          addOrUpdate("Project") {
            weight = 0.25f
            contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
          }
        }
        ToolWindowDescriptor.ToolWindowAnchor.RIGHT -> {
          addOrUpdate("Notifications") { weight = 0.25f }
        }
        ToolWindowDescriptor.ToolWindowAnchor.BOTTOM -> {
          addOrUpdate(id = "Version Control")
          addOrUpdate(id = "Find")
          addOrUpdate(id = "Run")
          addOrUpdate(id = "Debug") { weight = 0.4f }
          addOrUpdate(id = "Inspection") { weight = 0.4f}
        }
      }
    }

    override fun addPlatformDefaultsV2() {
      when (anchor) {
        ToolWindowDescriptor.ToolWindowAnchor.TOP -> throw UnsupportedOperationException("The top stripe is not supported")
        ToolWindowDescriptor.ToolWindowAnchor.LEFT -> {
          addOrUpdate("Project") {
            weight = 0.25f
            contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
          }
          addOrUpdate("Commit") { weight = 0.25f }
          addOrUpdate("Structure") {
            weight = 0.25f
            isSplit = true
          }
        }
        ToolWindowDescriptor.ToolWindowAnchor.RIGHT -> {
          addOrUpdate("Notifications") {
            weight = 0.25f
            contentUiType = ToolWindowDescriptor.ToolWindowContentUiType.COMBO
          }
          addOrUpdate("AIAssistant") { weight = 0.25f }
          addOrUpdate("Database") { weight = 0.25f }
          addOrUpdate("Gradle") { weight = 0.25f }
          addOrUpdate("Maven") { weight = 0.25f }
        }
        ToolWindowDescriptor.ToolWindowAnchor.BOTTOM -> {
          addOrUpdate("Version Control")
          // Auto-Build
          addOrUpdate("Problems")
          // Problems
          addOrUpdate("Problems View")
          addOrUpdate("Terminal")
          addOrUpdate("Services")
        }
      }
    }

    fun build(): List<ToolWindowDescriptor> = toolWindowBuilders.values
      .filter { it.anchor == anchor }
      .map {
        ToolWindowDescriptor(
          id = it.id,
          anchor = it.anchor,
          isVisible = it.isVisible,
          weight = it.weight,
          contentUiType = it.contentUiType,
          isSplit = it.isSplit,
          sideWeight = it.sideWeight,
        )
      }
  }

  private class DefaultToolWindowDescriptorBuilderImpl(override val id: String) : DefaultToolWindowDescriptorBuilder {
    override lateinit var anchor: ToolWindowDescriptor.ToolWindowAnchor
    override var isVisible: Boolean = false
    override var weight: Float = WindowInfoImpl.DEFAULT_WEIGHT
    override var contentUiType: ToolWindowDescriptor.ToolWindowContentUiType = ToolWindowDescriptor.ToolWindowContentUiType.TABBED
    override var isSplit: Boolean = false
    override var sideWeight: Float = 0.5f
  }
}

// Temporary bridges between old and new API

// old API using new API

@Internal
open class IntellijPlatformDefaultToolWindowLayoutProvider : DefaultToolWindowLayoutProvider {
  final override fun createV1Layout(): List<ToolWindowDescriptor> {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    val baseExtension = DefaultToolWindowLayoutPlatformExtension()
    baseExtension.buildV1Layout(builder)
    return builder.build()
  }

  override fun createV2Layout(): List<ToolWindowDescriptor> {
    val builder = DefaultToolWindowLayoutBuilderImpl()
    val baseExtension = DefaultToolWindowLayoutPlatformExtension()
    baseExtension.buildV2Layout(builder)
    return builder.build()
  }
}

// new API using old API

@Internal
class DefaultToolWindowLayoutProviderToExtensionAdapter : DefaultToolWindowLayoutExtension {
  override fun buildV1Layout(builder: DefaultToolWindowLayoutBuilder) {
    build(builder) { createV1Layout() }
  }

  override fun buildV2Layout(builder: DefaultToolWindowLayoutBuilder) {
    build(builder) { createV2Layout() }
  }

  private inline fun build(
    builder: DefaultToolWindowLayoutBuilder,
    method: DefaultToolWindowLayoutProvider.() -> List<ToolWindowDescriptor>
  ) {
    val descriptors = serviceOrNull<DefaultToolWindowLayoutProvider>()?.method() ?: return
    buildStripe(descriptors, builder.left, ToolWindowDescriptor.ToolWindowAnchor.LEFT)
    buildStripe(descriptors, builder.right, ToolWindowDescriptor.ToolWindowAnchor.RIGHT)
    buildStripe(descriptors, builder.bottom, ToolWindowDescriptor.ToolWindowAnchor.BOTTOM)
  }

  private fun buildStripe(
    descriptors: List<ToolWindowDescriptor>,
    stripe: DefaultToolWindowStripeBuilder,
    anchor: ToolWindowDescriptor.ToolWindowAnchor
  ) {
    descriptors.filter { it.anchor == anchor }.forEach {
      stripe.addOrUpdate(it.id) {
        isVisible = it.isVisible
        weight = it.weight
        contentUiType = it.contentUiType
        isSplit = it.isSplit
        sideWeight = it.sideWeight
      }
    }
  }
}
