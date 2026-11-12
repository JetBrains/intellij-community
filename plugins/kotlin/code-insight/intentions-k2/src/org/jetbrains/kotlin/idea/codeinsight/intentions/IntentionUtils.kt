// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.ui.awt.RelativePoint

/**
 * Runs [action] under progress and a read action when called on the EDT.
 * Otherwise, runs [action] directly.
 */
internal fun <T> computeWithProgressIconIfNeeded(editor: Editor, caretOffset: Int, action: () -> T): T {
    return if (ApplicationManager.getApplication().isDispatchThread) {
        val component = editor.contentComponent
        val point = RelativePoint(component, editor.logicalPositionToXY(editor.offsetToLogicalPosition(caretOffset)))
        computeWithProgressIcon(point, component, ActionPlaces.UNKNOWN) {
            readAction { action() }
        }
    } else {
        action()
    }
}
