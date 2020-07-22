package org.jetbrains.plugins.feature.suggester.actions.listeners

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.jetbrains.plugins.feature.suggester.FeatureSuggestersManager
import org.jetbrains.plugins.feature.suggester.actions.*

class PsiActionsListener(private val project: Project) : PsiTreeChangeAdapter() {
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        handleAction(
            BeforePropertyChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        handleAction(
            BeforeChildAddedAction(
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        handleAction(
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
            BeforeChildrenChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        handleAction(
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
            BeforeChildRemovedAction(
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
        handleAction(PropertyChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        handleAction(
            ChildRemovedAction(
                parent = event.parent,
                child = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleAction(
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
            ChildAddedAction(
                parent = event.parent,
                newChild = event.child,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleAction(ChildrenChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        handleAction(
            ChildMovedAction(
                parent = event.parent,
                child = event.child,
                oldParent = event.oldParent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    private fun handleAction(action: Action) {
        project.getService(FeatureSuggestersManager::class.java)
            ?.actionPerformed(action)
    }
}