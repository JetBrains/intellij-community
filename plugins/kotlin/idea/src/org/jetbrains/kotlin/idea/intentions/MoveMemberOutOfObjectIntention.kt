// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.refactoring.CompositeRefactoringRunner
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import java.util.function.Supplier

internal abstract class MoveMemberOutOfObjectIntention(textGetter: Supplier<@IntentionName String>) : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    textGetter
) {
    override fun startInWriteAction() = false

    abstract fun getDestination(element: KtNamedDeclaration): KtElement

    abstract fun addConflicts(element: KtNamedDeclaration, conflicts: MultiMap<PsiElement, String>)

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        val project = element.project

        val classOrObject = element.containingClassOrObject!!
        val destination = getDestination(element)

        fun deleteClassOrObjectIfEmpty() {
            if (classOrObject.declarations.isEmpty()) {
                classOrObject.delete()
            }
        }

        if (element is KtClassOrObject || !element.isPrivate() && destination is KtFile) {
            val moveDescriptor = MoveDeclarationsDescriptor(
              project,
              KotlinMoveSource(element),
              KotlinMoveTarget.ExistingElement(destination),
              KotlinMoveDeclarationDelegate.NestedClass()
            )

            val compositeRefactoringRunner = object : CompositeRefactoringRunner(project, MoveKotlinDeclarationsProcessor.REFACTORING_ID) {
                override fun runRefactoring() = MoveKotlinDeclarationsProcessor(moveDescriptor).run()
                override fun onExit() = runWriteAction { deleteClassOrObjectIfEmpty() }
            }

            if (element is KtClassOrObject) {
                compositeRefactoringRunner.run()
            } else {
                val conflicts = MultiMap<PsiElement, String>().apply { addConflicts(element, this) }
                project.checkConflictsInteractively(conflicts) {
                    compositeRefactoringRunner.run()
                }
            }
            return
        }

        val conflicts = MultiMap<PsiElement, String>().apply { addConflicts(element, this) }
        project.checkConflictsInteractively(conflicts) {
            runWriteAction {
                KotlinMover.Default(element, destination)
                deleteClassOrObjectIfEmpty()
            }
        }
    }
}