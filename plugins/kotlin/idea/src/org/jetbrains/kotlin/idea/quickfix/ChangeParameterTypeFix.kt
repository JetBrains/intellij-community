// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.DefinitelyNotNullType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ChangeParameterTypeFix(element: KtParameter, type: KotlinType) : KotlinQuickFixAction<KtParameter>(element) {
    private val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type)
    private val typeInfo =
        KotlinTypeInfo(isCovariant = false, text = IdeDescriptorRenderers.SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION.renderType(type))
    private val originalTypeText = type.safeAs<DefinitelyNotNullType>()?.original?.toString() ?: type.toString()

    private val containingDeclarationName: String?
    private val isPrimaryConstructorParameter: Boolean

    init {
        val declaration = PsiTreeUtil.getParentOfType(element, KtNamedDeclaration::class.java)
        this.containingDeclarationName = declaration?.name
        this.isPrimaryConstructorParameter = declaration is KtPrimaryConstructor
    }

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return containingDeclarationName != null
    }

    override fun getText(): String = element?.let {
        when {
            isPrimaryConstructorParameter -> {
                KotlinBundle.message(
                    "fix.change.return.type.text.primary.constructor",
                    it.name.toString(), containingDeclarationName.toString(), typePresentation
                )
            }
            else -> {
                KotlinBundle.message(
                    "fix.change.return.type.text.function",
                    it.name.toString(), containingDeclarationName.toString(), typePresentation
                )
            }
        }
    } ?: ""

    private val commandName: String
        @NlsContexts.Command
        get() = element?.let {
            when {
                isPrimaryConstructorParameter -> {
                    KotlinBundle.message(
                        "fix.change.return.type.command.primary.constructor",
                        it.name.toString(), containingDeclarationName.toString(), typePresentation
                    )
                }
                else -> {
                    KotlinBundle.message(
                        "fix.change.return.type.command.function",
                        it.name.toString(), containingDeclarationName.toString(), typePresentation
                    )
                }
            }
        } ?: ""

    override fun getFamilyName() = KotlinBundle.message("fix.change.return.type.family")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val function = element.getStrictParentOfType<KtFunction>() ?: return
        val parameterIndex = function.valueParameters.indexOf(element)
        val descriptor = function.resolveToDescriptorIfAny(BodyResolveMode.FULL) as? FunctionDescriptor ?: return
        val parameterType = descriptor.valueParameters[parameterIndex].type
        val typeParameterText = if (parameterType.isTypeParameter()) element.typeReference?.text else null
        val configuration = object : KotlinChangeSignatureConfiguration {
            override fun configure(originalDescriptor: KotlinMethodDescriptor) = originalDescriptor.apply {
                parameters[if (receiver != null) parameterIndex + 1 else parameterIndex].currentTypeInfo = typeInfo
            }

            override fun performSilently(affectedFunctions: Collection<PsiElement>) = true
        }
        runChangeSignature(element.project, editor, descriptor, configuration, element, commandName)

        if (typeParameterText != null && typeParameterText != originalTypeText) {
            runWriteAction {
                function.removeTypeParameter(typeParameterText)
            }
        }
    }

    private fun KtFunction.removeTypeParameter(typeParameterText: String?) {
        val typeParameterList = typeParameterList ?: return
        val typeParameters = typeParameterList.parameters
        val typeParameter = typeParameters.firstOrNull { it.name == typeParameterText } ?: return
        if (typeParameters.size == 1) typeParameterList.delete() else EditCommaSeparatedListHelper.removeItem(typeParameter)
    }
}
