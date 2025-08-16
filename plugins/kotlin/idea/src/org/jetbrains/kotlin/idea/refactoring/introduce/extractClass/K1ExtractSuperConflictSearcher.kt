// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.getPackage
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveConflictCheckerInfo
import org.jetbrains.kotlin.idea.refactoring.move.KotlinMoveTarget
import org.jetbrains.kotlin.idea.refactoring.move.checkAllConflicts
import org.jetbrains.kotlin.idea.refactoring.pullUp.checkVisibilityInAbstractedMembers
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier

internal class K1ExtractSuperConflictSearcher : KotlinExtractSuperConflictSearcher {
    override fun collectConflicts(
        originalClass: KtClassOrObject,
        memberInfos: List<KotlinMemberInfo>,
        targetParent: PsiElement,
        newClassName: String,
        isExtractInterface: Boolean
    ): MultiMap<PsiElement, String> {
        val conflicts = MultiMap<PsiElement, String>()

        val project = originalClass.project

        if (targetParent is KtElement) {
            val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent } as KtElement
            targetSibling.getResolutionScope()
                .findClassifier(Name.identifier(newClassName), NoLookupLocation.FROM_IDE)
                ?.let { DescriptorToSourceUtilsIde.getAnyDeclaration(project, it) }
                ?.let {
                    conflicts.putValue(
                        it,
                        KotlinBundle.message(
                            "text.class.0.already.exists.in.the.target.scope",
                            newClassName
                        )
                    )
                }
        }

        val elementsToMove = K1ExtractSuperRefactoring.getElementsToMove(memberInfos, originalClass, isExtractInterface).keys

        val moveTarget = if (targetParent is PsiDirectory) {
            val targetPackage = targetParent.getPackage() ?: return conflicts
            KotlinMoveTarget.DeferredFile(FqName(targetPackage.qualifiedName), targetParent.virtualFile)
        } else {
            KotlinMoveTarget.ExistingElement(targetParent as KtElement)
        }
        val conflictChecker = KotlinMoveConflictCheckerInfo(
            project,
            elementsToMove - memberInfos.asSequence().filter { it.isToAbstract }.mapNotNull { it.member }.toSet(),
            moveTarget,
            originalClass,
        )

        project.runSynchronouslyWithProgress(RefactoringBundle.message("detecting.possible.conflicts"), true) {
            runReadAction {
                val usages = LinkedHashSet<UsageInfo>()
                for (element in elementsToMove) {
                    ReferencesSearch.search(element).asIterable().mapTo(usages) { MoveRenameUsageInfo(it, element) }
                    if (element is KtCallableDeclaration) {
                        element.toLightMethods().flatMapTo(usages) {
                            MethodReferencesSearch.search(it).asIterable().map { reference -> MoveRenameUsageInfo(reference, element) }
                        }
                    }
                }
                conflicts.putAllValues(checkAllConflicts(conflictChecker, usages, LinkedHashSet()))
                if (targetParent is PsiDirectory) {
                    ExtractSuperClassUtil.checkSuperAccessible(targetParent, conflicts, originalClass.toLightClass())
                }

                checkVisibilityInAbstractedMembers(memberInfos, originalClass.getResolutionFacade(), conflicts)
            }
        }

        return conflicts
    }
}
