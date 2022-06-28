// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionManager

fun Editor.executeEnterHandler() {
    EditorActionManager.getInstance()
        .getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
        .execute(/* editor = */ this, /* contextCaret = */ null, /* dataContext = */ DataManager.getInstance().getDataContext(component))
}
