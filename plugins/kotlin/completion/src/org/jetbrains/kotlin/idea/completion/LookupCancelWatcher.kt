// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable

class LookupCancelWatcher : StartupActivity {
    override fun runActivity(project: Project) {
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorReleased(event: EditorFactoryEvent) {
                    LookupCancelService.getServiceIfCreated(project)?.disposeLastReminiscence(event.editor)
                }
            },
            KotlinPluginDisposable.getInstance(project),
        )
    }
}

class LookupCancelWatcherListener : LookupManagerListener {
    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (newLookup == null) return
        newLookup.addLookupListener(LookupCancelService.getInstance(newLookup.project).lookupCancelListener)
    }
}
