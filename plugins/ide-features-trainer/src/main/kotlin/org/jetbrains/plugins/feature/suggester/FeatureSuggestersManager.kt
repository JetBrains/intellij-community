package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.BackspaceAction
import com.intellij.openapi.editor.actions.CopyAction
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.ui.LightweightHint
import org.jetbrains.plugins.feature.suggester.changes.*
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.history.UserAnActionsHistory
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.suggesters.asString
import java.awt.Point

class FeatureSuggestersManager(val project: Project) : FileEditorManagerListener {
    private val MAX_ACTIONS_NUMBER: Int = 100
    private val actionsHistory = UserActionsHistory(MAX_ACTIONS_NUMBER)
    private val anActionsHistory = UserAnActionsHistory(MAX_ACTIONS_NUMBER)

    private var psiListenersIsSet: Boolean = false

    fun actionPerformed(action: UserAction) {
        actionsHistory.add(action)
        if (action.parent?.containingFile?.virtualFile == null) return
        processSuggesters()
    }

    fun anActionPerformed(anAction: UserAnAction) {
        anActionsHistory.add(anAction)
        processSuggesters()
    }

    private fun processSuggesters() {
        for (suggester in FeatureSuggester.suggesters) {
            if (!suggester.isEnabled()) continue
            processSuggester(suggester)
        }
    }

    private fun processSuggester(suggester: FeatureSuggester) {
        val suggestion = suggester.getSuggestion(actionsHistory, anActionsHistory)
        if (suggestion is PopupSuggestion) {
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
            override fun beforePropertyChange(event: PsiTreeChangeEvent) {
                actionPerformed(BeforePropertyChangedAction(event.parent))
            }

            override fun beforeChildAddition(event: PsiTreeChangeEvent) {
                actionPerformed(BeforeChildAddedAction(event.parent, event.child))
            }

            override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
                actionPerformed(BeforeChildReplacedAction(event.parent, event.newChild, event.oldChild))
            }

            override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
                actionPerformed(BeforeChildrenChangedAction(event.parent))
            }

            override fun beforeChildMovement(event: PsiTreeChangeEvent) {
                actionPerformed(BeforeChildMovedAction(event.parent, event.child, event.oldParent))
            }

            override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
                actionPerformed(BeforeChildRemovedAction(event.parent, event.child))
            }

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
                actionPerformed(ChildAddedAction(event.parent, event.child))
            }

            override fun childrenChanged(event: PsiTreeChangeEvent) {
                actionPerformed(ChildrenChangedAction(event.parent))
            }

            override fun childMoved(event: PsiTreeChangeEvent) {
                actionPerformed(ChildMovedAction(event.parent, event.child, event.oldParent))
            }
        })

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(AnActionListener.TOPIC, object : AnActionListener {
                private val copyPasteManager = CopyPasteManager.getInstance()

                override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
                    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
                    when (action) {
                        is CopyAction -> {
                            val copiedText = copyPasteManager.contents?.asString() ?: return
                            anActionPerformed(EditorCopyAction(copiedText, System.currentTimeMillis()))
                        }
                        is PasteAction -> {
                            val pastedText = copyPasteManager.contents?.asString() ?: return
                            val caretOffset = editor.getCaretOffset()
                            anActionPerformed(EditorPasteAction(pastedText, caretOffset, System.currentTimeMillis()))
                        }
                        is BackspaceAction -> {
                            val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
                            anActionPerformed(
                                EditorBackspaceAction(
                                    editor.getSelection(),
                                    editor.getCaretOffset(),
                                    psiFile,
                                    System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
                    val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return
                    when (action) {
                        is CopyAction -> {
                            val selectedText = editor.getSelectedText() ?: return
                            anActionPerformed(BeforeEditorCopyAction(selectedText, System.currentTimeMillis()))
                        }
                        is PasteAction -> {
                            val pastedText = copyPasteManager.contents?.asString() ?: return
                            val caretOffset = editor.getCaretOffset()
                            anActionPerformed(
                                BeforeEditorPasteAction(
                                    pastedText,
                                    caretOffset,
                                    System.currentTimeMillis()
                                )
                            )
                        }
                        is BackspaceAction -> {
                            val psiFile = dataContext.getData(CommonDataKeys.PSI_FILE) ?: return
                            anActionPerformed(
                                BeforeEditorBackspaceAction(
                                    editor.getSelection(),
                                    editor.getCaretOffset(),
                                    psiFile,
                                    System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                private fun Editor.getSelectedText(): String? {
                    return selectionModel.selectedText
                }

                private fun Editor.getCaretOffset(): Int {
                    return caretModel.offset
                }

                private fun Editor.getSelection(): Selection? {
                    with(selectionModel) {
                        return if (selectedText != null) {
                            Selection(selectionStart, selectionEnd, selectedText!!)
                        } else {
                            null
                        }
                    }
                }
            })

        psiListenersIsSet = true
    }

    private fun FeatureSuggester.isEnabled(): Boolean {
        return FeatureSuggesterSettings.isEnabled(getId())
    }
}