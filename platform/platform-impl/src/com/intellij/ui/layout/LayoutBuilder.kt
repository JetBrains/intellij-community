// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.ButtonGroup

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
open class LayoutBuilder @PublishedApi internal constructor(@PublishedApi internal val builder: LayoutBuilderImpl) : RowBuilder by builder.rootRow {

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use Kotlin UI DSL Version 2")
  override fun withButtonGroup(title: String?, buttonGroup: ButtonGroup, body: () -> Unit) {
    builder.withButtonGroup(buttonGroup, body)
  }

  @Suppress("PropertyName")
  @PublishedApi
  @get:Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
  @get:ApiStatus.ScheduledForRemoval
  internal val `$`: LayoutBuilderImpl
    get() = builder
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2", level = DeprecationLevel.HIDDEN)
class CellBuilderWithButtonGroupProperty<T : Any>
@PublishedApi internal constructor(private val prop: PropertyBinding<T>)


@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL Version 2")
class RowBuilderWithButtonGroupProperty<T : Any>
@PublishedApi internal constructor(private val builder: RowBuilder, private val prop: PropertyBinding<T>) : RowBuilder by builder {
}

fun FileChooserDescriptor.chooseFile(event: AnActionEvent, fileChosen: (chosenFile: VirtualFile) -> Unit) {
  FileChooser.chooseFile(this, event.getData(PlatformDataKeys.PROJECT), event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT), null,
                         fileChosen)
}
