package org.jetbrains.plugins.feature.suggester.listeners

import com.intellij.openapi.project.Project
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

class PsiActionsListener(private val project: Project) : PsiTreeChangeAdapter() {
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        if (event.parent == null) return
        handleAction(
            project,
            BeforePropertyChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null) return
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
        if (event.parent == null || event.newChild == null || event.oldChild == null) return
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
        if (event.parent == null) return
        handleAction(
            project,
            BeforeChildrenChangedAction(
                parent = event.parent,
                timeMillis = System.currentTimeMillis()
            )
        )
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || event.oldParent == null) return
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
        if (event.parent == null || event.child == null) return
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
        if (event.parent == null) return
        handleAction(project, PropertyChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null) return
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
        if (event.parent == null || event.newChild == null || event.oldChild == null) return
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
        if (event.parent == null || event.child == null) return
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
        if (event.parent == null) return
        handleAction(project, ChildrenChangedAction(parent = event.parent, timeMillis = System.currentTimeMillis()))
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
        if (event.parent == null || event.child == null || event.oldParent == null) return
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
