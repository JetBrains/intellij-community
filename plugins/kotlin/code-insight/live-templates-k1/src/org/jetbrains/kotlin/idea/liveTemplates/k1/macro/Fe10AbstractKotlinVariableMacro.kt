// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates.k1.macro

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.completion.BasicLookupElementFactory
import org.jetbrains.kotlin.idea.completion.InsertHandlerProvider
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.liveTemplates.macro.KotlinMacro
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

abstract class Fe10AbstractKotlinVariableMacro<State> : KotlinMacro() {
    private fun getVariables(params: Array<Expression>, context: ExpressionContext): Collection<VariableDescriptor> {
        if (params.isNotEmpty()) {
            return emptyList()
        }

        val project = context.project
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitAllDocuments()

        val document = context.editor?.document ?: return emptyList()
        val psiFile = psiDocumentManager.getPsiFile(document) as? KtFile ?: return emptyList()

        val contextElement = psiFile.findElementAt(context.startOffset)?.getNonStrictParentOfType<KtElement>() ?: return emptyList()

        val resolutionFacade = psiFile.getResolutionFacade()

        val bindingContext = resolutionFacade.analyze(contextElement, BodyResolveMode.PARTIAL_FOR_COMPLETION)

        fun isVisible(descriptor: DeclarationDescriptor): Boolean {
            return descriptor !is DeclarationDescriptorWithVisibility || descriptor.isVisible(
                contextElement,
                null,
                bindingContext,
                resolutionFacade
            )
        }

        val state = initState(contextElement, bindingContext)

        val helper = ReferenceVariantsHelper(
            bindingContext,
            resolutionFacade,
            resolutionFacade.moduleDescriptor,
            ::isVisible,
            NotPropertiesService.getNotProperties(contextElement)
        )

        return helper
            .getReferenceVariants(contextElement, CallTypeAndReceiver.DEFAULT, DescriptorKindFilter.VARIABLES, { true })
            .map { it as VariableDescriptor }
            .filter { isSuitable(it, project, state) }
    }

    protected abstract fun initState(contextElement: KtElement, bindingContext: BindingContext): State

    protected abstract fun isSuitable(
        variableDescriptor: VariableDescriptor,
        project: Project,
        state: State
    ): Boolean

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val vars = getVariables(params, context)
        if (vars.isEmpty()) return null
        return vars.firstOrNull()?.let { TextResult(it.name.render()) }
    }

    override fun calculateLookupItems(params: Array<Expression>, context: ExpressionContext): Array<LookupElement>? {
        val vars = getVariables(params, context)
        if (vars.size < 2) return null
        val lookupElementFactory = BasicLookupElementFactory(context.project, InsertHandlerProvider(CallType.DEFAULT, editor = context.editor!!) { emptyList() })
        return vars.map { lookupElementFactory.createLookupElement(it) }.toTypedArray()
    }
}
