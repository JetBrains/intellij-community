package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.components.common.toolwindows.DebugToolWindowUi
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.list

/** The Frames list of the Debug tool window. The selected item is the frame the editor shows. */
internal fun DebugToolWindowUi.framesList(): JListUiComponent = list { byClass("XDebuggerFramesList") }
