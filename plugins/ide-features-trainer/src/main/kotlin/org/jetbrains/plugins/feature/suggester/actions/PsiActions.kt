package org.jetbrains.plugins.feature.suggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

sealed class PsiAction : Action() {
    abstract val parent: PsiElement
    override val language: Language
        get() = parent.language
    override val project: Project
        get() = parent.project
}

// -------------------------------------PSI AFTER ACTIONS-------------------------------------
data class ChildrenChangedAction(
    override val parent: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class ChildAddedAction(
    override val parent: PsiElement,
    val newChild: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class ChildReplacedAction(
    override val parent: PsiElement,
    val newChild: PsiElement,
    val oldChild: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class ChildRemovedAction(
    override val parent: PsiElement,
    val child: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class PropertyChangedAction(
    override val parent: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class ChildMovedAction(
    override val parent: PsiElement,
    val child: PsiElement,
    val oldParent: PsiElement,
    override val timeMillis: Long
) : PsiAction()

// -------------------------------------PSI BEFORE ACTIONS-------------------------------------
data class BeforeChildrenChangedAction(
    override val parent: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class BeforeChildAddedAction(
    override val parent: PsiElement,
    val newChild: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class BeforeChildReplacedAction(
    override val parent: PsiElement,
    val newChild: PsiElement,
    val oldChild: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class BeforeChildRemovedAction(
    override val parent: PsiElement,
    val child: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class BeforePropertyChangedAction(
    override val parent: PsiElement,
    override val timeMillis: Long
) : PsiAction()

data class BeforeChildMovedAction(
    override val parent: PsiElement,
    val child: PsiElement,
    val oldParent: PsiElement,
    override val timeMillis: Long
) : PsiAction()
