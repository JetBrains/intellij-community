// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility.LOCAL
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.AddThrowsAnnotationIntention.Context
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.wasm.isWasm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.types.Variance.INVARIANT

private val KOTLIN_ARRAY_OF_FQ_NAME = FqName("kotlin.arrayOf")

/**
 * Creates a `@Throws` annotation entry for an exception at the caret,
 * or adds this exception to the existing annotation entry of the containing declaration.
 *
 * Tests:
 *   - [org.jetbrains.kotlin.idea.k2.intentions.tests.K2IntentionTestGenerated.AddThrowsAnnotation]
 *   - [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated.AddThrowsAnnotation]
 */
internal class AddThrowsAnnotationIntention : KotlinApplicableModCommandAction<KtThrowExpression, Context>(KtThrowExpression::class) {
    class Context(
        val annotationArgumentText: String,
        val containingDeclaration: SmartPsiElementPointer<KtDeclaration>,
        val throwsAnnotation: SmartPsiElementPointer<KtAnnotationEntry>?
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.throws.annotation")

    override fun isApplicableByPsi(element: KtThrowExpression): Boolean {
        if (element.platform.isJs() || element.platform.isWasm()) return false
        if (element.containingDeclaration() == null) return false
        return true
    }

    override fun KaSession.prepareContext(element: KtThrowExpression): Context? {
        val type = element.thrownExpression?.expressionType ?: return null
        if (type.symbol?.visibility == LOCAL) {
            // Can't expose local declaration in the `throws` clause
            return null
        }

        val annotationArgumentText = type.asAnnotationArgumentText()
        val containingDeclaration = element.containingDeclaration() ?: return null
        val throwsAnnotation = containingDeclaration.findExistingThrowsAnnotation()
        val context = Context(
            annotationArgumentText,
            containingDeclaration.createSmartPointer(),
            throwsAnnotation?.createSmartPointer()
        )

        if (throwsAnnotation == null) {
            // No existing `throws` annotation, we will create a new one
            return context
        }

        val valueArguments = throwsAnnotation.valueArguments.ifEmpty { return context }
        val firstArgument = valueArguments.first().getArgumentExpression()

        if (firstArgument is KtCallExpression) {
            // Annotation arguments should be constants, so function calls are not allowed (except `arrayOf`)
            val functionCall = firstArgument.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            val fqName = functionCall.symbol.callableId?.asSingleFqName() ?: return null
            if (fqName != KOTLIN_ARRAY_OF_FQ_NAME) return null
        }

        if (valueArguments.any { it.containsType(type) }) {
            // The type is already declared, nothing to do
            return null
        }

        return context
    }

    override fun invoke(actionContext: ActionContext, element: KtThrowExpression, elementContext: Context, updater: ModPsiUpdater) {
        val annotationArgumentText = elementContext.annotationArgumentText
        val containingDeclaration = elementContext.containingDeclaration.element ?: return
        val throwsAnnotation = elementContext.throwsAnnotation?.element

        if (throwsAnnotation == null || throwsAnnotation.valueArguments.isEmpty()) {
            createNewAnnotation(throwsAnnotation, containingDeclaration, annotationArgumentText)
        } else {
            addToExistingAnnotation(throwsAnnotation, annotationArgumentText)
        }
    }

    private fun createNewAnnotation(existingAnnotation: KtAnnotationEntry?, declaration: KtDeclaration, argumentText: String) {
        existingAnnotation?.delete()
        declaration.addAnnotation(JvmStandardClassIds.Annotations.ThrowsAlias, argumentText, searchForExistingEntry = false)
    }

    private fun addToExistingAnnotation(throwsAnnotation: KtAnnotationEntry, argumentText: String) {
        val psiFactory = KtPsiFactory(throwsAnnotation.project)
        val firstValueArgument = throwsAnnotation.valueArguments.firstOrNull()
        val expression = firstValueArgument?.getArgumentExpression()

        val addedArgument = when {
            // @Throws(FooException::class, BarException::class)
            firstValueArgument?.getArgumentName() == null -> {
                val newArgument = psiFactory.createArgument(argumentText)
                throwsAnnotation.valueArgumentList?.addArgument(newArgument)
            }

            // @Throws(exceptionClasses = arrayOf(FooException::class))
            expression is KtCallExpression -> {
                val newArgument = psiFactory.createArgument(argumentText)
                expression.valueArgumentList?.addArgument(newArgument)
            }

            // @Throws(exceptionClasses = FooException::class)
            expression is KtClassLiteralExpression -> {
                val collectionLiteral = psiFactory.createCollectionLiteral(listOf(expression), argumentText)
                expression.replaced(collectionLiteral).getInnerExpressions().lastOrNull()
            }

            // @Throws(exceptionClasses = [FooException::class])
            expression is KtCollectionLiteralExpression -> {
                val newCollectionLiteral = psiFactory.createCollectionLiteral(expression.getInnerExpressions(), argumentText)
                expression.replaced(newCollectionLiteral).getInnerExpressions().lastOrNull()
            }

            else -> null
        }

        if (addedArgument != null) {
            shortenReferences(addedArgument)
        }
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaType.asAnnotationArgumentText(): String {
    // Account for typealiases: we want to render `RuntimeException` instead of `java.lang.RuntimeException`
    val typeToRender = this.abbreviation ?: this
    val escapedFqName = typeToRender.render(position = INVARIANT)
    return "$escapedFqName::class"
}

private fun KtThrowExpression.containingDeclaration(): KtDeclaration? {
    val parent = getParentOfTypes(
        strict = true,
        KtNamedFunction::class.java,
        KtSecondaryConstructor::class.java,
        KtPropertyAccessor::class.java,
        KtClassInitializer::class.java,
        KtLambdaExpression::class.java
    )
    if (parent is KtClassInitializer || parent is KtLambdaExpression) return null
    return parent as? KtDeclaration
}

context(_: KaSession)
private fun KtDeclaration.findExistingThrowsAnnotation(): KtAnnotationEntry? {
    val annotations = this.annotationEntries + (parent as? KtProperty)?.annotationEntries.orEmpty()
    return annotations.find { annotation ->
        val classId = annotation.typeReference?.type?.symbol?.classId
        classId == JvmStandardClassIds.Annotations.ThrowsAlias || classId == JvmStandardClassIds.Annotations.Throws
    }
}

context(_: KaSession)
private fun ValueArgument.containsType(type: KaType): Boolean {
    val classLiteralExpressions = when (val argumentExpression = getArgumentExpression()) {
        is KtClassLiteralExpression -> listOf(argumentExpression)

        is KtCollectionLiteralExpression ->
            argumentExpression.getInnerExpressions().filterIsInstance<KtClassLiteralExpression>()

        is KtCallExpression ->
            argumentExpression.valueArguments.mapNotNull { it.getArgumentExpression() as? KtClassLiteralExpression }

        else -> emptyList()
    }

    return classLiteralExpressions.any {
        // Ex. KClass<MyException>
        val kClassType = it.expressionType as? KaClassType ?: return@any false
        kClassType.typeArguments.firstOrNull()?.type?.semanticallyEquals(type) == true
    }
}

private fun KtPsiFactory.createCollectionLiteral(expressions: List<KtExpression>, lastExpression: String): KtCollectionLiteralExpression {
    val text = (expressions.map { it.text } + lastExpression).joinToString(prefix = "[", postfix = "]")
    return createExpression(text) as KtCollectionLiteralExpression
}