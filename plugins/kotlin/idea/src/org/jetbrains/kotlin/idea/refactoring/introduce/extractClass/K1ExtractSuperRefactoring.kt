// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractClass

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.actions.createKotlinFileFromTemplate
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.core.quoteSegmentsIfNeeded
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.introduce.insertDeclaration
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.getChildrenToAnalyze
import org.jetbrains.kotlin.idea.refactoring.memberInfo.toJavaMemberInfo
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addIfNotNull

internal class K1ExtractSuperRefactoring private constructor(): KotlinExtractSuperRefactoring {
    companion object {
        internal fun getElementsToMove(
            memberInfos: Collection<KotlinMemberInfo>,
            originalClass: KtClassOrObject,
            isExtractInterface: Boolean
        ): Map<KtElement, KotlinMemberInfo?> {
            val project = originalClass.project
            val elementsToMove = LinkedHashMap<KtElement, KotlinMemberInfo?>()
            runReadAction {
                val superInterfacesToMove = ArrayList<KtElement>()
                for (memberInfo in memberInfos) {
                    val member = memberInfo.member ?: continue
                    if (memberInfo.isSuperClass) {
                        superInterfacesToMove += member
                    } else {
                        elementsToMove[member] = memberInfo
                    }
                }

                val superTypeList = originalClass.getSuperTypeList()
                if (superTypeList != null) {
                    for (superTypeListEntry in originalClass.superTypeListEntries) {
                        val superType =
                            superTypeListEntry.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, superTypeListEntry.typeReference]
                                ?: continue
                        val superClassDescriptor = superType.constructor.declarationDescriptor ?: continue
                        val superClass = DescriptorToSourceUtilsIde.getAnyDeclaration(project, superClassDescriptor) as? KtClass ?: continue
                        if ((!isExtractInterface && !superClass.isInterface()) || superClass in superInterfacesToMove) {
                            elementsToMove[superTypeListEntry] = null
                        }
                    }
                }
            }
            return elementsToMove
        }
    }

    private fun collectTypeParameters(
        extractInfo: ExtractSuperInfo,
        refTarget: PsiElement?,
        typeParameters: MutableSet<KtTypeParameter>,
    ) = with(typeParameters) {
        if (refTarget is KtTypeParameter && refTarget.getStrictParentOfType<KtTypeParameterListOwner>() == extractInfo.originalClass) {
            add(refTarget)
            refTarget.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                        addIfNotNull(expression.mainReference.resolve() as? KtTypeParameter)
                    }
                }
            )
        }
    }

    private fun analyzeContext(extractInfo: ExtractSuperInfo): Set<KtTypeParameter> {
        val typeParameters = LinkedHashSet<KtTypeParameter>()
        val visitor = object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val refTarget = expression.mainReference.resolve()
                collectTypeParameters(extractInfo, refTarget, typeParameters)
            }
        }
        getElementsToMove(extractInfo.memberInfos, extractInfo.originalClass, extractInfo.isInterface)
            .asSequence()
            .flatMap {
                val (element, info) = it
                info?.getChildrenToAnalyze()?.asSequence() ?: sequenceOf(element)
            }
            .forEach { it.accept(visitor) }
        return typeParameters
    }

    private fun createClass(
        extractInfo: ExtractSuperInfo,
        bindingContext: BindingContext,
        superClassEntry: KtSuperTypeListEntry?,
        typeParameters: Set<KtTypeParameter>,
    ): KtClass? {
        val project = extractInfo.originalClass.project
        val targetParent = extractInfo.targetParent
        val newClassName = extractInfo.newClassName.quoteIfNeeded()
        val originalClass = extractInfo.originalClass
        val psiFactory = KtPsiFactory(project)

        val kind = if (extractInfo.isInterface) "interface" else "class"
        val prototype = psiFactory.createClass("$kind $newClassName")
        val newClass = if (targetParent is PsiDirectory) {
            val file = targetParent.findFile(extractInfo.targetFileName) ?: run {
                val template = FileTemplateManager.getInstance(project).getInternalTemplate("Kotlin File")
                createKotlinFileFromTemplate(extractInfo.targetFileName, template, targetParent) ?: return null
            }
            file.add(prototype) as KtClass
        } else {
            val targetSibling = originalClass.parentsWithSelf.first { it.parent == targetParent }
            insertDeclaration(prototype, targetSibling)
        }

        val shouldBeAbstract = extractInfo.memberInfos.any { it.isToAbstract }
        if (!extractInfo.isInterface) {
            newClass.addModifier(if (shouldBeAbstract) KtTokens.ABSTRACT_KEYWORD else KtTokens.OPEN_KEYWORD)
        }

        if (typeParameters.isNotEmpty()) {
            val typeParameterListText = typeParameters.sortedBy { it.startOffset }.joinToString(prefix = "<", postfix = ">") { it.text }
            newClass.addAfter(psiFactory.createTypeParameterList(typeParameterListText), newClass.nameIdentifier)
        }

        val targetPackageFqName = (targetParent as? PsiDirectory)?.getFqNameWithImplicitPrefix()?.quoteSegmentsIfNeeded()

        val superTypeText = buildString {
            if (!targetPackageFqName.isNullOrEmpty()) {
                append(targetPackageFqName).append('.')
            }
            append(newClassName)
            if (typeParameters.isNotEmpty()) {
                append(typeParameters.sortedBy { it.startOffset }.map { it.name }.joinToString(prefix = "<", postfix = ">"))
            }
        }
        val needSuperCall = !extractInfo.isInterface
                && (superClassEntry is KtSuperTypeCallEntry
                || originalClass.hasPrimaryConstructor()
                || originalClass.secondaryConstructors.isEmpty())
        val newSuperTypeListEntry = if (needSuperCall) {
            psiFactory.createSuperTypeCallEntry("$superTypeText()")
        } else {
            psiFactory.createSuperTypeEntry(superTypeText)
        }
        if (superClassEntry != null) {
            val qualifiedTypeRefText = bindingContext[BindingContext.TYPE, superClassEntry.typeReference]?.let {
                IdeDescriptorRenderers.SOURCE_CODE.renderType(it)
            }
            val superClassEntryToAdd = if (qualifiedTypeRefText != null) {
                superClassEntry.copied().apply { typeReference?.replace(psiFactory.createType(qualifiedTypeRefText)) }
            } else superClassEntry
            newClass.addSuperTypeListEntry(superClassEntryToAdd)
            ShortenReferences.DEFAULT.process(superClassEntry.replaced(newSuperTypeListEntry))
        } else {
            ShortenReferences.DEFAULT.process(originalClass.addSuperTypeListEntry(newSuperTypeListEntry))
        }

        ShortenReferences.DEFAULT.process(newClass)

        return newClass
    }

    override fun performRefactoring(extractInfo: ExtractSuperInfo) {
        val project = extractInfo.originalClass.project
        val originalClass = extractInfo.originalClass

        val handler = if (extractInfo.isInterface) KotlinExtractInterfaceHandler else KotlinExtractSuperclassHandler
        handler.getErrorMessage(originalClass)?.let { throw CommonRefactoringUtil.RefactoringErrorHintException(it) }

        val bindingContext = extractInfo.originalClass.analyze(BodyResolveMode.PARTIAL)

        val superClassEntry = if (!extractInfo.isInterface) {
            val originalClassDescriptor = originalClass.unsafeResolveToDescriptor() as ClassDescriptor
            val superClassDescriptor = originalClassDescriptor.getSuperClassNotAny()
            originalClass.superTypeListEntries.firstOrNull {
                bindingContext[BindingContext.TYPE, it.typeReference]?.constructor?.declarationDescriptor == superClassDescriptor
            }
        } else null

        val typeParameters = project.runSynchronouslyWithProgress(
            progressTitle = RefactoringBundle.message("progress.text"),
            canBeCanceled = true,
        ) {
            runReadAction { analyzeContext(extractInfo) }
        } ?: emptySet()

        project.executeWriteCommand(KotlinExtractSuperclassHandler.REFACTORING_NAME) {
            val newClass = createClass(
                extractInfo,
                bindingContext,
                superClassEntry,
                typeParameters,
            ) ?: return@executeWriteCommand

            val subClass = extractInfo.originalClass.toLightClass() ?: return@executeWriteCommand
            val superClass = newClass.toLightClass() ?: return@executeWriteCommand

            PullUpProcessor(
                subClass,
                superClass,
                extractInfo.memberInfos.mapNotNull { it.toJavaMemberInfo() }.toTypedArray(),
                extractInfo.docPolicy
            ).moveMembersToBase()

            performDelayedRefactoringRequests(project)
        }
    }
}
