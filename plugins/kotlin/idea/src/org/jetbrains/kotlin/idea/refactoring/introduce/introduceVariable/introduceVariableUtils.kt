// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.analysis.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.util.isValidOperator

private fun getApplicableComponentFunctions(
    contextExpression: KtExpression,
    receiverType: KotlinType?,
    receiverExpression: KtExpression?
): List<FunctionDescriptor> {
    val facade = contextExpression.getResolutionFacade()
    val context = facade.analyze(contextExpression)
    val builtIns = facade.moduleDescriptor.builtIns

    val forbiddenClasses = arrayListOf(builtIns.collection, builtIns.array)
    PrimitiveType.values().mapTo(forbiddenClasses) { builtIns.getPrimitiveArrayClassDescriptor(it) }

    (receiverType ?: context.getType(contextExpression))?.let {
        if ((listOf(it) + it.supertypes()).any { type ->
                val fqName = type.constructor.declarationDescriptor?.importableFqName
                forbiddenClasses.any { descriptor -> descriptor.fqNameSafe == fqName }
            }
        ) return emptyList()
    }

    val scope = contextExpression.getResolutionScope(context, facade)

    val psiFactory = KtPsiFactory(contextExpression)
    @Suppress("UNCHECKED_CAST")
    return generateSequence(1) { it + 1 }.map {
            val componentCallExpr = psiFactory.createExpressionByPattern("$0.$1", receiverExpression ?: contextExpression, "component$it()")
            val newContext = componentCallExpr.analyzeInContext(scope, contextExpression)
            componentCallExpr.getResolvedCall(newContext)?.resultingDescriptor as? FunctionDescriptor
        }
        .takeWhile { it != null && it.isValidOperator() }
        .toList() as List<FunctionDescriptor>
}

internal fun chooseApplicableComponentFunctions(
    contextExpression: KtExpression,
    editor: Editor?,
    type: KotlinType? = null,
    receiverExpression: KtExpression? = null,
    callback: (List<FunctionDescriptor>) -> Unit
) {
    val functions = getApplicableComponentFunctions(contextExpression, type, receiverExpression)
    if (functions.size <= 1) return callback(emptyList())

    if (isUnitTestMode()) return callback(functions)

    if (editor == null) return callback(emptyList())

    val singleVariable = KotlinBundle.message("text.create.single.variable")
    val listOfVariants = listOf(
        singleVariable,
        KotlinBundle.message("text.create.destructuring.declaration"),
    )

    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(listOfVariants)
        .setMovable(true)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback { callback(if (it == singleVariable) emptyList() else functions) }
        .createPopup()
        .showInBestPositionFor(editor)
}

internal fun suggestNamesForComponent(descriptor: FunctionDescriptor, project: Project, validator: (String) -> Boolean): Set<String> {
    return LinkedHashSet<String>().apply {
        val descriptorName = descriptor.name.asString()
        val componentName = (DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor) as? PsiNamedElement)?.name
            ?: descriptorName
        if (componentName == descriptorName) {
            descriptor.returnType?.let { addAll(KotlinNameSuggester.suggestNamesByType(it, validator)) }
        }
        add(KotlinNameSuggester.suggestNameByName(componentName, validator))
    }
}