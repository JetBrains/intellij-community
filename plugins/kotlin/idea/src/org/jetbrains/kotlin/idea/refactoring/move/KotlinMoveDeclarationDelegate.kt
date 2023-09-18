// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.moveInner.MoveInnerClassUsagesHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

sealed interface KotlinMoveDeclarationDelegate {
    fun getContainerChangeInfo(originalDeclaration: KtNamedDeclaration, moveTarget: KotlinMoveTarget): MoveContainerChangeInfo

    fun findInternalUsages(moveSource: KotlinMoveSource): List<UsageInfo> = emptyList()

    fun collectConflicts(moveTarget: KotlinMoveTarget, internalUsages: MutableSet<UsageInfo>, conflicts: MultiMap<PsiElement, String>) {}

    fun preprocessDeclaration(moveTarget: KotlinMoveTarget, originalDeclaration: KtNamedDeclaration) {}

    fun preprocessUsages(project: Project, moveSource: KotlinMoveSource, usages: List<UsageInfo>) {}

    object TopLevel : KotlinMoveDeclarationDelegate {
        override fun getContainerChangeInfo(
            originalDeclaration: KtNamedDeclaration,
            moveTarget: KotlinMoveTarget
        ): MoveContainerChangeInfo {
            val sourcePackage = MoveContainerInfo.Package(originalDeclaration.containingKtFile.packageFqName)
            val targetPackage = moveTarget.targetContainerFqName?.let { MoveContainerInfo.Package(it) } ?: MoveContainerInfo.UnknownPackage
            return MoveContainerChangeInfo(sourcePackage, targetPackage)
        }
    }

    class NestedClass(
        val newClassName: String? = null,
        private val outerInstanceParameterName: String? = null
    ) : KotlinMoveDeclarationDelegate {
        override fun getContainerChangeInfo(
            originalDeclaration: KtNamedDeclaration,
            moveTarget: KotlinMoveTarget
        ): MoveContainerChangeInfo {
            val originalInfo = MoveContainerInfo.Class(originalDeclaration.containingClassOrObject!!.fqName!!)
            val movingToClass = (moveTarget as? KotlinMoveTarget.ExistingElement)?.targetElement is KtClassOrObject
            val targetContainerFqName = moveTarget.targetContainerFqName
            val newInfo = when {
                targetContainerFqName == null -> MoveContainerInfo.UnknownPackage
                movingToClass -> MoveContainerInfo.Class(targetContainerFqName)
                else -> MoveContainerInfo.Package(targetContainerFqName)
            }
            return MoveContainerChangeInfo(originalInfo, newInfo)
        }

        override fun findInternalUsages(moveSource: KotlinMoveSource): List<UsageInfo> {
            val classToMove = moveSource.elementsToMove.singleOrNull() as? KtClass ?: return emptyList()
            return collectOuterInstanceReferences(classToMove)
        }

        override fun collectConflicts(
            moveTarget: KotlinMoveTarget,
            internalUsages: MutableSet<UsageInfo>,
            conflicts: MultiMap<PsiElement, String>
        ) {
            val usageIterator = internalUsages.iterator()
            while (usageIterator.hasNext()) {
                val usage = usageIterator.next()
                val element = usage.element ?: continue

                val isConflict = when (usage) {
                    is ImplicitCompanionAsDispatchReceiverUsageInfo -> {
                        val isValidTarget = isValidTargetForImplicitCompanionAsDispatchReceiver(moveTarget, usage.companionObject)
                        if (!isValidTarget) {
                            conflicts.putValue(
                                element,
                                KotlinBundle.message("text.implicit.companion.object.will.be.inaccessible.0", element.text)
                            )
                        }
                        true
                    }

                    is OuterInstanceReferenceUsageInfo -> usage.reportConflictIfAny(conflicts)

                    else -> false
                }
                if (isConflict) {
                    usageIterator.remove()
                }
            }
        }

        override fun preprocessDeclaration(moveTarget: KotlinMoveTarget, originalDeclaration: KtNamedDeclaration) {
            with(originalDeclaration) {
                newClassName?.let { setName(it) }

                if (this is KtClass) {
                    if ((moveTarget as? KotlinMoveTarget.ExistingElement)?.targetElement !is KtClassOrObject) {
                        if (hasModifier(KtTokens.INNER_KEYWORD)) removeModifier(KtTokens.INNER_KEYWORD)
                        if (hasModifier(KtTokens.PROTECTED_KEYWORD)) removeModifier(KtTokens.PROTECTED_KEYWORD)
                    }

                    if (outerInstanceParameterName != null) {
                        val containingClass = containingClassOrObject ?: return
                        val type = renderType(containingClass)
                        val parameter = KtPsiFactory(project).createParameter("private val $outerInstanceParameterName: $type")
                        val addedParameter = createPrimaryConstructorParameterListIfAbsent().addParameter(parameter)
                        addDelayedShorteningRequest(addedParameter)
                    }
                }
            }
        }

        override fun preprocessUsages(project: Project, moveSource: KotlinMoveSource, usages: List<UsageInfo>) {
            if (outerInstanceParameterName == null) return
            val psiFactory = KtPsiFactory(project)
            val newOuterInstanceRef = psiFactory.createExpression(outerInstanceParameterName)
            val classToMove = moveSource.elementsToMove.singleOrNull() as? KtClass

            for (usage in usages) {
                if (usage is MoveRenameUsageInfo) {
                    val referencedNestedClass = usage.referencedElement?.unwrapped as? KtClassOrObject
                    if (referencedNestedClass == classToMove) {
                        val outerClass = referencedNestedClass?.containingClassOrObject
                        val lightOuterClass = outerClass?.toLightClass()
                        if (lightOuterClass != null) {
                            MoveInnerClassUsagesHandler.EP_NAME
                                .forLanguage(usage.element?.language ?: return)
                                ?.correctInnerClassUsage(usage, lightOuterClass, outerInstanceParameterName)
                        }
                    }
                }

                when (usage) {
                    is OuterInstanceReferenceUsageInfo.ExplicitThis -> {
                        usage.expression.replace(newOuterInstanceRef)
                    }

                    is OuterInstanceReferenceUsageInfo.ImplicitReceiver -> {
                        usage.callElement.let { it.replace(psiFactory.createExpressionByPattern("$0.$1", outerInstanceParameterName, it)) }
                    }
                }
            }
        }
    }
}