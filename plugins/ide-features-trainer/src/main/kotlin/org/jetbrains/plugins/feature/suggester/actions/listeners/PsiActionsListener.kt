package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.plugins.feature.suggester.actions.*
import org.jetbrains.plugins.feature.suggester.suggesters.handleAction

class PsiActionsListener(private val project: Project) : PsiTreeChangeAdapter() {
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforePropertyChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforeChildAddedAction(
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforeChildReplacedAction(
                parent = event.parent,
                newChild = event.newChild,
                oldChild = event.oldChild,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforeChildrenChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforeChildMovedAction(
                parent = event.parent,
                child = event.child,
                oldParent = event.oldParent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            BeforeChildRemovedAction(
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        handleAction(project, PropertyChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            ChildRemovedAction(
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            ChildReplacedAction(
                parent = event.parent,
                newChild = event.newChild,
                oldChild = event.oldChild,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            ChildAddedAction(
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleAction(project, ChildrenChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        handleAction(
            project,
            ChildMovedAction(
                parent = event.parent,
                child = event.child,
                oldParent = event.oldParent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }
}