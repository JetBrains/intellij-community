// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import org.jetbrains.kotlin.idea.completion.implCommon.LookupCancelService

class LookupCancelWatcher : EditorFactoryListener {
    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        LookupCancelService.getServiceIfCreated(project)?.disposeLastReminiscence(editor)
    }
}

class LookupCancelWatcherListener : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (newLookup == null) return
        newLookup.addLookupListener(LookupCancelService.getInstance(newLookup.project).lookupCancelListener)
    }
}
