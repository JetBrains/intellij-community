package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.ui.LightweightHint
import org.jetbrains.plugins.feature.suggester.cache.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsHistory
import org.jetbrains.plugins.feature.suggester.changes.*
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import java.awt.Point

class FeatureSuggestersManager(val project: Project) : FileEditorManagerListener {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsCache = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val anActionsCache = UserAnActionsHistory(MAX_ACTIONS_NUMBER)

    private var psiListenersIsSet: Boolean = false

    fun actionPerformed(action: UserAction) {
        actionsCache.add(action)
        for (suggester in FeatureSuggester.suggesters) {
            if (!isEnabled(suggester)) continue
            val suggestion = suggester.getSuggestion(actionsCache, anActionsCache)
            if (suggestion is PopupSuggestion) {
                action.parent?.containingFile?.virtualFile ?: return
                val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                if (suggester.needToClearLookup) {
                    //todo: this is hack to avoid exception in spection completion case
                    val lookupManager = LookupManager.getInstance(project)
                    lookupManager as LookupManagerImpl
                    lookupManager.clearLookup()
                }
                showSuggestionHint(suggestion.message, editor)

                // send event for testing
                project.messageBus.syncPublisher(FeatureSuggestersManagerListener.TOPIC).featureFound(suggestion)
                return
            }
        }
    }

    private fun showSuggestionHint(message: String, editor: Editor) {
        val label = HintUtil.createQuestionLabel(message)
        //val hint: LightweightHint = PatchedLightweightHint(label)     can't create java.lang.NoClassDefFoundError PatchedLightweightHint
        //todo: this is hack to avoid hiding on parameter info popup
        val hint = LightweightHint(label)
        val hintManager = HintManager.getInstance()
        hintManager as HintManagerImpl
        val point: Point = hintManager.getHintPosition(hint, editor, HintManager.ABOVE)
        IdeTooltipManager.getInstance().hideCurrentNow(false)
        hintManager.showEditorHint(hint, editor, point, HintManager.HIDE_BY_ESCAPE, 0, false)
    }

    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile
    ) {
        if (project != source.project || psiListenersIsSet)
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
    }

    private fun isEnabled(suggester: FeatureSuggester): Boolean {
        return FeatureSuggesterSettings.isEnabled(suggester.getId())
    }
}