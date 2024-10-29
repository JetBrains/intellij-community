// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.migLayout.MigLayoutBuilder
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import javax.swing.JComponent

@PublishedApi
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
internal fun createLayoutBuilder(): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration()))
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
interface LayoutBuilderImpl {
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  val rootRow: Row

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val preferredFocusedComponent: JComponent?

  // Validators applied when Apply is pressed
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val validateCallbacks: List<() -> ValidationInfo?>

  // Validators applied immediately on input
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  val componentValidateCallbacks: Map<JComponent, () -> ValidationInfo?>

  // Validation applicants for custom validation events
  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  val customValidationRequestors: Map<JComponent, List<(() -> Unit) -> Unit>>

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  val applyCallbacks: Map<JComponent?, List<() -> Unit>>

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  val resetCallbacks: Map<JComponent?, List<() -> Unit>>

  @get:Deprecated("Use Kotlin UI DSL Version 2")
  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @Deprecated("Use Kotlin UI DSL Version 2")
  val isModifiedCallbacks: Map<JComponent?, List<() -> Boolean>>
}

// https://jetbrains.github.io/ui/controls/input_field/#spacing
@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
private fun createIntelliJSpacingConfiguration(): SpacingConfiguration {
  return object : SpacingConfiguration {
    override val horizontalGap = JBUI.scale(6)
    override val componentVerticalGap = JBUI.scale(6)
    override val labelColumnHorizontalGap = JBUI.scale(6)
    override val largeHorizontalGap = JBUI.scale(16)
    override val largeVerticalGap = JBUI.scale(20)

    override val shortTextWidth = JBUI.scale(250)
    override val maxShortTextWidth = JBUI.scale(350)

    override val unitSize = JBUI.scale(4)

    override val dialogTopBottom = JBUI.scale(10)
    override val dialogLeftRight = JBUI.scale(12)

    override val commentVerticalTopGap = JBUI.scale(6)

    override val indentLevel: Int
      get() = JBUI.scale(20)
  }
}
