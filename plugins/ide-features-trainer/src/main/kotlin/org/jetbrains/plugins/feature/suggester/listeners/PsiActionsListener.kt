package org.jetbrains.plugins.feature.suggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildMovedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.actions.BeforePropertyChangedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildMovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.actions.PropertyChangedAction
import org.jetbrains.plugins.feature.suggester.handleAction
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport

class PsiActionsListener(private val project: Project) : PsiTreeChangeAdapter() {
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        if (event.parent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforePropertyChangedAction(
                psiFile = event.file!!,
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforeChildAddedAction(
                psiFile = event.file!!,
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.newChild == null || event.oldChild == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforeChildReplacedAction(
                psiFile = event.file!!,
                parent = event.parent,
                newChild = event.newChild,
                oldChild = event.oldChild,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        if (event.parent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforeChildrenChangedAction(
                psiFile = event.file!!,
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || event.oldParent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforeChildMovedAction(
                psiFile = event.file!!,
                parent = event.parent,
                child = event.child,
                oldParent = event.oldParent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            BeforeChildRemovedAction(
                psiFile = event.file!!,
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        if (event.parent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            PropertyChangedAction(
                psiFile = event.file!!,
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            ChildRemovedAction(
                psiFile = event.file!!,
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.newChild == null || event.oldChild == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            ChildReplacedAction(
                psiFile = event.file!!,
                parent = event.parent,
                newChild = event.newChild,
                oldChild = event.oldChild,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            ChildAddedAction(
                psiFile = event.file!!,
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        if (event.parent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            ChildrenChangedAction(
                psiFile = event.file!!,
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || event.oldParent == null || !isSourceFile(event.file)) return
        handleAction(
            project,
            ChildMovedAction(
                psiFile = event.file!!,
                parent = event.parent,
                child = event.child,
                oldParent = event.oldParent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    private fun isSourceFile(psiFile: PsiFile?): Boolean {
        val language = psiFile?.language ?: return false
        return LanguageSupport.getForLanguage(language)?.isSourceFile(psiFile) == true
    }
}
