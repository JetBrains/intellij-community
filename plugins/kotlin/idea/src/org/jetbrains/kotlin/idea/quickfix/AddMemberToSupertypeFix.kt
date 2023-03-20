// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.AddMemberToSupertypeFix.MemberData
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.Icon

abstract class AddMemberToSupertypeFix(element: KtCallableDeclaration, private val candidateMembers: List<MemberData>) :
  KotlinQuickFixAction<KtCallableDeclaration>(element), LowPriorityAction {

    class MemberData(val signaturePreview: String, val sourceCode: String, val targetClass: KtClass)

    init {
        assert(candidateMembers.isNotEmpty())
    }

    abstract val kind: String
    abstract val icon: Icon

    override fun getText(): String =
        candidateMembers.singleOrNull()?.let { actionName(it) } ?: KotlinBundle.message("fix.add.member.supertype.text", kind)

    override fun getFamilyName() = KotlinBundle.message("fix.add.member.supertype.family", kind)

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        CommandProcessor.getInstance().runUndoTransparentAction {
            if (candidateMembers.size == 1 || editor == null || !editor.component.isShowing) {
                addMember(candidateMembers.first(), project)
            } else {
                JBPopupFactory.getInstance().createListPopup(createMemberPopup(project)).showInBestPositionFor(editor)
            }
        }
    }

    private fun addMember(memberData: MemberData, project: Project) {
        project.executeWriteCommand(KotlinBundle.message("fix.add.member.supertype.progress", kind)) {
            element?.removeDefaultParameterValues()
            val classBody = memberData.targetClass.getOrCreateBody()
            val memberElement: KtCallableDeclaration = KtPsiFactory(project).createDeclaration(memberData.sourceCode)
            memberElement.copyAnnotationEntriesFrom(element)
            val insertedMemberElement = classBody.addBefore(memberElement, classBody.rBrace) as KtCallableDeclaration
            ShortenReferences.DEFAULT.process(insertedMemberElement)
            val modifierToken = insertedMemberElement.modalityModifier()?.node?.elementType as? KtModifierKeywordToken
                ?: return@executeWriteCommand
            if (insertedMemberElement.implicitModality() == modifierToken) {
                RemoveModifierFixBase(insertedMemberElement, modifierToken, true).invoke()
            }
        }
    }

    private fun KtCallableDeclaration.removeDefaultParameterValues() {
        valueParameters.forEach {
            it.defaultValue?.delete()
            it.equalsToken?.delete()
        }
    }

    private fun KtCallableDeclaration.copyAnnotationEntriesFrom(member: KtCallableDeclaration?) {
        member?.annotationEntries?.reversed()?.forEach { addAnnotationEntry(it) }
    }

    private fun createMemberPopup(project: Project): ListPopupStep<*> {
        return object : BaseListPopupStep<MemberData>(KotlinBundle.message("fix.add.member.supertype.choose.type"), candidateMembers) {
            override fun isAutoSelectionEnabled() = false

            override fun onChosen(selectedValue: MemberData, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    addMember(selectedValue, project)
                }
                return PopupStep.FINAL_CHOICE
            }

            override fun getIconFor(value: MemberData) = icon
            override fun getTextFor(value: MemberData) = actionName(value)
        }
    }

    @Nls
    private fun actionName(memberData: MemberData): String =
        KotlinBundle.message(
            "fix.add.member.supertype.add.to",
            memberData.signaturePreview, memberData.targetClass.name.toString()
        )
}

abstract class AddMemberToSupertypeFactory : KotlinSingleIntentionActionFactory() {
    protected fun getCandidateMembers(memberElement: KtCallableDeclaration): List<MemberData> {
        val descriptors = generateCandidateMembers(memberElement)
        return descriptors.mapNotNull { createMemberData(it, memberElement) }
    }

    abstract fun createMemberData(memberDescriptor: CallableMemberDescriptor, memberElement: KtCallableDeclaration): MemberData?

    private fun generateCandidateMembers(memberElement: KtCallableDeclaration): List<CallableMemberDescriptor> {
        val memberDescriptor =
            memberElement.resolveToDescriptorIfAny(BodyResolveMode.FULL) as? CallableMemberDescriptor ?: return emptyList()
        val containingClass = memberDescriptor.containingDeclaration as? ClassDescriptor ?: return emptyList()
        // TODO: filter out impossible supertypes (for example when argument's type isn't visible in a superclass).
        return getKotlinSourceSuperClasses(containingClass).map { generateMemberSignatureForType(memberDescriptor, it) }
    }

    private fun getKotlinSourceSuperClasses(classDescriptor: ClassDescriptor): List<ClassDescriptor> {
        val supertypes = classDescriptor.defaultType.supertypes().toMutableList().sortSubtypesFirst()
        return supertypes.mapNotNull { type ->
            type.constructor.declarationDescriptor.safeAs<ClassDescriptor>().takeIf {
                it !is JavaClassDescriptor && it !is DeserializedClassDescriptor
            }
        }
    }

    private fun MutableList<KotlinType>.sortSubtypesFirst(): List<KotlinType> {
        val typeChecker = KotlinTypeChecker.DEFAULT
        for (i in 1 until size) {
            val currentType = this[i]
            for (j in 0 until i) {
                if (typeChecker.isSubtypeOf(currentType, this[j])) {
                    this.removeAt(i)
                    this.add(j, currentType)
                    break
                }
            }
        }
        return this
    }

    private fun generateMemberSignatureForType(
        memberDescriptor: CallableMemberDescriptor,
        typeDescriptor: ClassDescriptor
    ): CallableMemberDescriptor {
        // TODO: support for generics.
        val modality = if (typeDescriptor.kind == ClassKind.INTERFACE || typeDescriptor.modality == Modality.SEALED) {
            Modality.ABSTRACT
        } else {
            typeDescriptor.modality
        }

        return memberDescriptor.copy(
            typeDescriptor,
            modality,
            memberDescriptor.visibility,
            CallableMemberDescriptor.Kind.DECLARATION,
            /* copyOverrides = */ false
        )
    }
}

class AddFunctionToSupertypeFix private constructor(element: KtNamedFunction, functions: List<MemberData>) :
    AddMemberToSupertypeFix(element, functions) {

    override val kind: String = "function"
    override val icon: Icon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function)

    companion object : AddMemberToSupertypeFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val functionElement = diagnostic.psiElement as? KtNamedFunction ?: return null
            val candidateFunctions = getCandidateMembers(functionElement)
            return if (candidateFunctions.isNotEmpty()) AddFunctionToSupertypeFix(functionElement, candidateFunctions) else null
        }

        override fun createMemberData(memberDescriptor: CallableMemberDescriptor, memberElement: KtCallableDeclaration): MemberData? {
            val classDescriptor = memberDescriptor.containingDeclaration as ClassDescriptor
            val project = memberElement.project
            var sourceCode =
                IdeDescriptorRenderers.SOURCE_CODE.withNoAnnotations().withDefaultValueOption(project).render(memberDescriptor)
            if (classDescriptor.kind != ClassKind.INTERFACE && memberDescriptor.modality != Modality.ABSTRACT) {
                val returnType = memberDescriptor.returnType
                sourceCode += if (returnType == null || !KotlinBuiltIns.isUnit(returnType)) {
                    val bodyText = getFunctionBodyTextFromTemplate(
                        project,
                        TemplateKind.FUNCTION,
                        memberDescriptor.name.asString(),
                        memberDescriptor.returnType?.let { IdeDescriptorRenderers.SOURCE_CODE.renderType(it) } ?: "Unit",
                        classDescriptor.importableFqName
                    )
                    "{\n$bodyText\n}"
                } else {
                    "{}"
                }
            }

            val targetClass = DescriptorToSourceUtilsIde.getAnyDeclaration(project, classDescriptor) as? KtClass ?: return null
            return MemberData(
                IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withDefaultValueOption(project).render(memberDescriptor),
                sourceCode,
                targetClass
            )
        }
    }
}

class AddPropertyToSupertypeFix private constructor(element: KtProperty, properties: List<MemberData>) :
    AddMemberToSupertypeFix(element, properties) {

    override val kind: String = "property"
    override val icon: Icon = PlatformIcons.PROPERTY_ICON

    companion object : AddMemberToSupertypeFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val propertyElement = diagnostic.psiElement as? KtProperty ?: return null
            val candidateProperties = getCandidateMembers(propertyElement)
            return if (candidateProperties.isNotEmpty()) AddPropertyToSupertypeFix(propertyElement, candidateProperties) else null
        }

        override fun createMemberData(memberDescriptor: CallableMemberDescriptor, memberElement: KtCallableDeclaration): MemberData? {
            val classDescriptor = memberDescriptor.containingDeclaration as ClassDescriptor
            val signaturePreview = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.render(memberDescriptor)
            var sourceCode = IdeDescriptorRenderers.SOURCE_CODE.withNoAnnotations().render(memberDescriptor)
            val initializer = memberElement.safeAs<KtProperty>()?.initializer
            if (classDescriptor.kind == ClassKind.CLASS && classDescriptor.modality == Modality.OPEN && initializer != null) {
                sourceCode += " = ${initializer.text}"
            }
            val targetClass = DescriptorToSourceUtilsIde.getAnyDeclaration(memberElement.project, classDescriptor) as? KtClass ?: return null
            return MemberData(signaturePreview, sourceCode, targetClass)
        }
    }
}

private fun DescriptorRenderer.withNoAnnotations(): DescriptorRenderer {
    return withOptions {
        modifiers -= DescriptorRendererModifier.ANNOTATIONS
    }
}

private fun DescriptorRenderer.withDefaultValueOption(project: Project): DescriptorRenderer {
    return withOptions {
        this.defaultParameterValueRenderer = {
            OptionalParametersHelper.defaultParameterValueExpression(it, project)?.text
                ?: error("value parameter renderer shouldn't be called when there is no value to render")
        }
    }
}