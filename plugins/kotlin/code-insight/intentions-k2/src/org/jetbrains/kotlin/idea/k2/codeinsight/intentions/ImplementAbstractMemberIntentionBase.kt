// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import com.intellij.util.IncorrectOperationException
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.ClassListCellRenderer
import org.jetbrains.kotlin.idea.core.overrideImplement.*
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.ImplementationStatus.ALREADY_IMPLEMENTED
import org.jetbrains.kotlin.util.ImplementationStatus.INHERITED_OR_SYNTHESIZED

private val LOG = Logger.getInstance(ImplementAbstractMemberIntentionBase::class.java.canonicalName)

abstract class ImplementAbstractMemberIntentionBase : SelfTargetingRangeIntention<KtNamedDeclaration>(
    KtNamedDeclaration::class.java,
    KotlinBundle.messagePointer("implement.abstract.member"),
) {

    override fun startInWriteAction(): Boolean = false

    protected abstract fun computeText(element: KtNamedDeclaration): (() -> String)?

    private fun isApplicable(element: KtNamedDeclaration): Boolean = findImplementableMembers(element).any()

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (!element.isAbstract()) return null

        setTextGetter(computeText(element) ?: return null)

        val isApplicable = if (application.isDispatchThread) {
            val editor = element.findExistingEditor()!!
            val aComponent = editor.contentComponent
            val point = RelativePoint(aComponent, editor.logicalPositionToXY(editor.offsetToLogicalPosition(element.startOffset)))
            computeWithProgressIcon(point, aComponent, ActionPlaces.UNKNOWN) {
                readAction { isApplicable(element) }
            }
        } else {
            isApplicable(element)
        }

        if (!isApplicable) return null
        return element.nameIdentifier?.textRange
    }

    protected abstract fun createImplementableMember(
        targetClass: PsiElement,
        abstractMember: KtNamedDeclaration,
    ): ImplementableMember?

    override fun applyTo(element: KtNamedDeclaration, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val project = element.project

        val implementableMembers = runWithModalProgressBlocking(
            project,
            KotlinBundle.message("intention.implement.abstract.method.searching.for.descendants.progress"),
        ) {
            runReadAction {
                findImplementableMembers(element).toList()
            }
        }

        if (implementableMembers.isEmpty()) return

        implementableMembers.singleOrNull()?.let {
            return it.generateMembers(editor)
        }

        val classRenderer = ClassListCellRenderer()
        val classComparator = classRenderer.comparator
        val sortedImplementableMembers = implementableMembers.sortedWith { o1, o2 ->
            classComparator.compare(o1.getTargetClass(), o2.getTargetClass())
        }

        if (isUnitTestMode()) {
            return implementTargetMembers(project, sortedImplementableMembers)
        }

        buildTargetPopupWithMultiSelect(
            items = sortedImplementableMembers,
            presentationProvider = {
                runReadAction {
                    val targetClass = it.getTargetClass()
                    TargetPresentation.builder(classRenderer.getElementText(targetClass)!!)
                        .icon(targetClass.getIcon(0))
                        .presentation()
                }
            }) { true }
            .setTitle(CodeInsightBundle.message("intention.implement.abstract.method.class.chooser.title"))
            .setItemsChosenCallback {
                implementTargetMembers(project, it)
            }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    private fun implementTargetMembers(
        project: Project,
        implementableMembers: Collection<ImplementableMember>,
    ) {
        project.executeCommand(KotlinBundle.message("intention.implement.abstract.method.command.name")) {
            val targetClasses = implementableMembers.map(ImplementableMember::getTargetClass)
            if (!FileModificationService.getInstance().preparePsiElementsForWrite(targetClasses)) return@executeCommand
            for (implementableMember in implementableMembers) {
                try {
                    val descriptor = OpenFileDescriptor(project, implementableMember.getTargetClass().containingFile.virtualFile)
                    val targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, /* focusEditor = */ true)
                    implementableMember.generateMembers(targetEditor)
                } catch (e: IncorrectOperationException) {
                    LOG.error(e)
                }
            }
        }
    }

    private fun findImplementableMembers(
        abstractMember: KtNamedDeclaration,
    ): Sequence<ImplementableMember> {
        val baseClass = abstractMember.containingClassOrObject as? KtClass ?: return emptySequence()

        fun createImplementableMember(targetClass: PsiElement): ImplementableMember? {
            if (!BaseIntentionAction.canModify(targetClass)) return null
            return createImplementableMember(targetClass, abstractMember)
        }

        return if (baseClass.isEnum()) {
            baseClass.declarations
                .asSequence()
                .filterIsInstance<KtEnumEntry>()
                .mapNotNull(::createImplementableMember)
        } else {
            DirectKotlinClassInheritorsSearch.search(baseClass).asIterable().asSequence().mapNotNull(::createImplementableMember)
        }
    }

    protected sealed interface ImplementableMember {

        fun generateMembers(editor: Editor?)
        fun getTargetClass(): PsiElement

        class KtImplementableMember private constructor(
            private val origin: KtClassOrObject,
            private val targetClass: PsiElement,
            private val member: KtClassMember,
        ) : ImplementableMember {

            override fun generateMembers(editor: Editor?) {
                KtOverrideMembersHandler().generateMembers(
                    editor = editor!!,
                    classOrObject = origin,
                    selectedElements = listOf(member),
                    copyDoc = false,
                )
            }

            override fun getTargetClass(): PsiElement {
                return targetClass
            }

            companion object {

                fun from(
                    targetClass: KtClass,
                    abstractMember: KtNamedDeclaration,
                    preferConstructorParameters: Boolean,
                ): KtImplementableMember? {
                    val ktClassMember = analyze(targetClass) {
                        val subClass = targetClass.symbol as? KaClassSymbol ?: return null
                        if (subClass.classKind == KaClassKind.INTERFACE) return null
                        val existingImplementation = findExistingImplementation(subClass, abstractMember)
                        if (existingImplementation != null) return null
                        val symbolToImplement = getCallableMemberToImplement(abstractMember, subClass) ?: return null
                        createKtClassMember(symbolToImplement, preferConstructorParameters)
                    }
                    return KtImplementableMember(
                        targetClass = targetClass,
                        member = ktClassMember,
                        origin = targetClass,
                    )
                }

                @OptIn(KaExperimentalApi::class)
                private fun KaSession.findExistingImplementation(
                    targetClass: KaClassSymbol,
                    abstractMember: KtNamedDeclaration,
                ): KaCallableSymbol? {
                    val superMember = abstractMember.symbol as? KaCallableSymbol ?: return null
                    val superClass = superMember.containingDeclaration as? KaClassSymbol ?: return null
                    val substitutor = createInheritanceTypeSubstitutor(targetClass, superClass) ?: return null
                    val signatureInSubClass = superMember.substitute(substitutor)
                    return targetClass.memberScope.findCallableMemberBySignature(signatureInSubClass)?.takeIf {
                        val implementationStatus = it.getImplementationStatus(targetClass)
                        implementationStatus == ALREADY_IMPLEMENTED || implementationStatus == INHERITED_OR_SYNTHESIZED
                    }
                }

                @OptIn(KaExperimentalApi::class)
                fun from(
                    targetClass: KtEnumEntry,
                    abstractMember: KtNamedDeclaration,
                    preferConstructorParameters: Boolean,
                ): KtImplementableMember? {
                    val ktClassMember = analyze(targetClass) {
                        val symbol = targetClass.symbol
                        val enumEntryInitializer = symbol.enumEntryInitializer
                        val existingImplementation = enumEntryInitializer?.memberScope?.findCallableMemberBySignature(symbol.asSignature())
                        if (existingImplementation != null) return null
                        val symbolToImplement = abstractMember.symbol as? KaCallableSymbol ?: return null
                        createKtClassMember(symbolToImplement, preferConstructorParameters)
                    }
                    return KtImplementableMember(
                        targetClass = targetClass,
                        member = ktClassMember,
                        origin = targetClass,
                    )
                }

                @OptIn(KaExperimentalApi::class)
                private fun KaSession.getCallableMemberToImplement(
                    abstractMember: KtNamedDeclaration,
                    subClass: KaDeclarationContainerSymbol,
                ): KaCallableSymbol? {
                    val superMember = abstractMember.symbol as? KaCallableSymbol ?: return null
                    val superClass = superMember.containingDeclaration as? KaClassSymbol ?: return null
                    val substitution = createInheritanceTypeSubstitutor(subClass as KaClassSymbol, superClass) ?: return null
                    val signatureToImplement = superMember.substitute(substitution)
                    return subClass.memberScope.findCallableMemberBySignature(signatureToImplement)
                }

                @OptIn(KaExperimentalApi::class)
                private fun KaSession.createKtClassMember(
                    symbolToImplement: KaCallableSymbol,
                    preferConstructorParameters: Boolean,
                ): KtClassMember = KtClassMember(
                    memberInfo = KtClassMemberInfo.create(
                        symbol = symbolToImplement,
                        memberText = symbolToImplement.render(KtGenerateMembersHandler.renderer),
                    ),
                    bodyType = BodyType.FromTemplate,
                    preferConstructorParameter = preferConstructorParameters,
                )
            }
        }

        class JavaImplementableMember private constructor(
            private val targetClass: PsiClass,
            private val abstractMember: KtNamedDeclaration,
        ) : ImplementableMember {

            companion object {
                fun from(
                    targetClass: PsiClass,
                    abstractMember: KtNamedDeclaration,
                ): JavaImplementableMember? {
                    if (targetClass.isInterface) return null
                    return JavaImplementableMember(targetClass, abstractMember)
                }
            }

            override fun generateMembers(editor: Editor?) {
                runWriteAction {  abstractMember.toLightMethods().forEach { OverrideImplementUtil.overrideOrImplement(targetClass, it) } }
            }

            override fun getTargetClass(): PsiElement {
                return targetClass
            }
        }
    }
}
