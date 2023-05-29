// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.ui

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.MoveCallback
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.move.*
import org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.*
import org.jetbrains.kotlin.idea.statistics.KotlinMoveRefactoringFUSCollector.MoveRefactoringDestination
import org.jetbrains.kotlin.idea.statistics.KotlinMoveRefactoringFUSCollector.MovedEntity
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement

internal class MoveKotlinNestedClassesModel(
    val project: Project,
    val openInEditorCheckBox: Boolean,
    val selectedElementsToMove: List<KtClassOrObject>,
    val originalClass: KtClassOrObject,
    val targetClass: PsiElement?,
    val moveCallback: MoveCallback?
) : Model {

    private fun getCheckedTargetClass(): KtElement {
        val targetClass = this.targetClass ?: throw ConfigurationException(RefactoringBundle.message("no.destination.class.specified"))

        if (targetClass !is KtClassOrObject) {
            throw ConfigurationException(KotlinBundle.message("text.destination.class.should.be.kotlin.class"))
        }

        if (originalClass === targetClass) {
            throw ConfigurationException(RefactoringBundle.message("source.and.destination.classes.should.be.different"))
        }

        for (classOrObject in selectedElementsToMove) {
            if (PsiTreeUtil.isAncestor(classOrObject, targetClass, false)) {
                throw ConfigurationException(
                    KotlinBundle.message("text.cannot.move.inner.class.0.into.itself", classOrObject.name.toString())
                )
            }
        }

        return targetClass
    }

    @Throws(ConfigurationException::class)
    override fun computeModelResult() = computeModelResult(throwOnConflicts = false)

    @Throws(ConfigurationException::class)
    override fun computeModelResult(throwOnConflicts: Boolean): ModelResultWithFUSData {
        val elementsToMove = selectedElementsToMove
        val target = KotlinMoveTarget.ExistingElement(getCheckedTargetClass())
        val delegate = KotlinMoveDeclarationDelegate.NestedClass()
        val descriptor = MoveDeclarationsDescriptor(
            project,
            KotlinMoveSource(elementsToMove),
            target,
            delegate,
            searchInCommentsAndStrings = false,
            searchInNonCode = false,
            deleteSourceFiles = false,
            moveCallback = moveCallback,
            openInEditor = openInEditorCheckBox
        )

        val processor = MoveKotlinDeclarationsProcessor(descriptor, KotlinMover.Default, throwOnConflicts)

        return ModelResultWithFUSData(
          processor,
          elementsToMove.size,
          MovedEntity.CLASSES,
          MoveRefactoringDestination.DECLARATION
        )
    }
}