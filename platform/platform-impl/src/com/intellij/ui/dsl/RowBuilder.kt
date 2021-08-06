// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JComponent

@DslMarker
private annotation class RowBuilderMarker

@ApiStatus.Experimental
@RowBuilderMarker
interface RowBuilder {

  fun <T : JComponent> cell(component: T): CellBuilder<T>

  fun panel(init: PanelBuilder.() -> Unit): PanelBuilder

  fun checkBox(@NlsContexts.Checkbox text: String): CellBuilder<JBCheckBox>

  fun button(@NlsContexts.Button text: String, actionListener: (event: ActionEvent) -> Unit): CellBuilder<JButton>

}
