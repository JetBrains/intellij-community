package org.jetbrains.plugins.feature.suggester

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.plugins.feature.suggester.cache.UserActionsCache
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsCache
import org.jetbrains.plugins.feature.suggester.changes.*
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings

class FeatureSuggestersManager(val project: Project) : FileEditorManagerListener {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsCache = UserActionsCache(MAX_ACTIONS_NUMBER)
    private val anActionsCache = UserAnActionsCache(MAX_ACTIONS_NUMBER)

    private var psiListenersIsSet: Boolean = false

    fun actionPerformed(action: UserAction) {
        actionsCache.add(action)
        for (suggester in FeatureSuggester.suggesters) {
            if (!isEnabled(suggester)) continue
            val suggestion = suggester.getSuggestion(actionsCache, anActionsCache)
            if (suggestion is PopupSuggestion) {
                println("Action performed: ${suggestion.message}")
            }
        }
    }

    override fun fileOpenedSync(
        source: FileEditorManager,
        file: VirtualFile,
        editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>
    ) {
        if(project != source.project || psiListenersIsSet)
            return

        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
            override fun propertyChanged(event: PsiTreeChangeEvent) {
                actionPerformed(PropertyChangedAction(event.parent))
            }

            override fun childRemoved(event: PsiTreeChangeEvent) {
                actionPerformed(ChildRemovedAction(event.parent, event.child))
            }

            override fun childReplaced(event: PsiTreeChangeEvent) {
                actionPerformed(ChildReplacedAction(event.parent, event.newChild, event.oldChild))
            }

            override fun childAdded(event: PsiTreeChangeEvent) {
                actionPerformed(ChildAddedAction(event.parent, event.newChild))
            }

            override fun childrenChanged(event: PsiTreeChangeEvent) {
                actionPerformed(ChildrenChangedAction(event.parent))
            }

            override fun childMoved(event: PsiTreeChangeEvent) {
                actionPerformed(ChildMovedAction(event.parent, event.child, event.oldParent))
            }
        })

        psiListenersIsSet = true
        println("PSI listeners have set")
    }

    private fun isEnabled(suggester: FeatureSuggester): Boolean {
        return FeatureSuggesterSettings.isEnabled(suggester.getId())
    }
}