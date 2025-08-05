// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Inspection to convert a Kotlin secondary constructor to a primary constructor.
 *
 * This inspection targets Kotlin secondary constructors that can be refactored into primary
 * constructors. It identifies secondary constructors that are redundant and facilitates their
 * conversion to primary constructors, improving code readability and conciseness.
 */
internal class ConvertSecondaryConstructorToPrimaryInspection :
    KotlinApplicableInspectionBase.Simple<KtSecondaryConstructor, ConvertSecondaryConstructorToPrimaryInspection.SecondaryConstructorContext>() {

    internal class SecondaryConstructorContext(
        /** parameter name to property in the physical class */
        val parameterToPropertyMap: MutableMap<String, KtProperty>,
        /** parameters which can be properties of the primary constructor */
        val parametersAsProperties: Set<String>,
        /** non-physical holder with constructor statements which were not converted to assignments */
        val initializer: KtAnonymousInitializer,
        /** can keep init {} on the constructor place  */
        val hasPropertyAfterInitializer: Boolean,
        /** index of the superclass reference which argument list should be replaced */
        val classRefToUpdate: Int
    )

    override fun getProblemDescription(
        element: KtSecondaryConstructor,
        context: SecondaryConstructorContext
    ): @InspectionMessage String = KotlinBundle.message("convert.to.primary.constructor.before.text")

    override fun isApplicableByPsi(element: KtSecondaryConstructor): Boolean {
        val delegationCall = element.getDelegationCall()
        if (delegationCall.isCallToThis) return false
        val klass = element.containingClassOrObject ?: return false
        return !klass.hasPrimaryConstructor()
    }

    override fun getApplicableRanges(element: KtSecondaryConstructor): List<TextRange> {
        return listOf(TextRange(0, element.valueParameterList?.textRangeInParent?.endOffset ?: element.getConstructorKeyword().textLength))
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
            visitTargetElement(constructor, holder, isOnTheFly)
        }
    }

    context(_: KaSession)
    private tailrec fun KtSecondaryConstructor.isReachableByDelegationFrom(
        constructor: KtSecondaryConstructor, visited: Set<KtSecondaryConstructor> = emptySet()
    ): Boolean {
        if (constructor == this) return true
        if (constructor in visited) return false
        val delegatedConstructor = constructor.getDelegationCall().resolveToCall()
            ?.singleConstructorCallOrNull()?.partiallyAppliedSymbol?.symbol?.psi as? KtSecondaryConstructor ?: return false
        return isReachableByDelegationFrom(delegatedConstructor, visited + constructor)
    }

    override fun KaSession.prepareContext(secondaryConstructor: KtSecondaryConstructor): SecondaryConstructorContext? {
        val klass = secondaryConstructor.containingClassOrObject ?: return null

        for (constructorDescriptor in klass.secondaryConstructors) {
            if (constructorDescriptor == secondaryConstructor) continue
            if (!secondaryConstructor.isReachableByDelegationFrom(constructorDescriptor)) return null
        }

        val psiFactory = KtPsiFactory.contextual(secondaryConstructor)

        val parameterToPropertyMap = mutableMapOf<String, KtProperty>()
        val parametersAsProperties = mutableSetOf<String>()

        val initializer = psiFactory.createAnonymousInitializer()
        var hasPropertyAfterInitializer = false
        val endOffsetOfConstructor = secondaryConstructor.textRange.endOffset
        secondaryConstructor.bodyExpression?.statements?.forEach { statement ->
            val parameterToProperty = statement.tryConvertToPropertyByParameterInitialization(secondaryConstructor)
            if (parameterToProperty != null) {
                val (rightTarget, leftTarget) = parameterToProperty

                val parameterName = rightTarget.name!!
                parameterToPropertyMap[parameterName] = leftTarget

                val isSafeToUseAsProperty = parameterName == leftTarget.name &&
                        rightTarget.returnType.semanticallyEquals(leftTarget.returnType) &&
                        leftTarget.accessors.all { it.symbol.isDefault }
                if (isSafeToUseAsProperty) {
                    parametersAsProperties.add(parameterName)
                }
            } else {
                (initializer.body as? KtBlockExpression)?.let {
                    it.addBefore(statement.copy(), it.rBrace)
                    it.addBefore(psiFactory.createNewLine(), it.rBrace)
                }

                if (!hasPropertyAfterInitializer) {
                    statement.accept(object : KtTreeVisitorVoid() {
                        override fun visitReferenceExpression(expression: KtReferenceExpression) {
                            val resolve = expression.mainReference.resolve()
                            if (resolve is KtProperty && resolve.containingClassOrObject == klass &&
                                // if property is declared after constructor
                                resolve.textOffset > endOffsetOfConstructor
                            ) {
                                hasPropertyAfterInitializer = true
                            }
                        }
                    })
                }
            }
        }

        val classRefIdx = klass.superTypeListEntries.indexOfFirst {
            val classifierSymbol =
                (it.typeReference?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolveToSymbol() as? KaClassifierSymbol

            fun isClassSymbol(symbol: KaClassifierSymbol?): Boolean = symbol is KaClassSymbol && symbol.classKind == KaClassKind.CLASS

            classifierSymbol is KaTypeAliasSymbol && isClassSymbol((classifierSymbol.expandedType as? KaClassType)?.symbol) || isClassSymbol(
                classifierSymbol
            )
        }

        return SecondaryConstructorContext(
            parameterToPropertyMap,
            parametersAsProperties,
            initializer,
            hasPropertyAfterInitializer,
            classRefIdx
        )
    }

    private fun KtExpression.tryConvertToPropertyByParameterInitialization(
        constructor: KtConstructor<*>
    ): Pair<KtParameter, KtProperty>? {
        if (this !is KtBinaryExpression || operationToken != KtTokens.EQ) return null
        val rightReference = right as? KtReferenceExpression ?: return null
        val rightTarget = rightReference.mainReference.resolve() as? KtParameter ?: return null
        if (rightTarget.ownerFunction != constructor) return null
        if (rightTarget.name == null) return null
        val leftReference = when (val left = left) {
            is KtReferenceExpression ->
                left

            is KtDotQualifiedExpression ->
                if (left.receiverExpression is KtThisExpression) left.selectorExpression as? KtReferenceExpression else null

            else ->
                null
        }
        val leftTarget = leftReference?.mainReference?.resolve() as? KtProperty ?: return null
        if (leftTarget.containingClassOrObject != constructor.containingClassOrObject) return null
        return rightTarget to leftTarget
    }

    override fun createQuickFix(
        element: KtSecondaryConstructor,
        elementContext: SecondaryConstructorContext
    ): KotlinModCommandQuickFix<KtSecondaryConstructor> = object : KotlinModCommandQuickFix<KtSecondaryConstructor>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.primary.constructor")
        override fun applyFix(
            project: Project,
            secondaryConstructor: KtSecondaryConstructor,
            updater: ModPsiUpdater
        ) {
            val writableSecondaryConstructor = updater.getWritable(secondaryConstructor)
            val klass = writableSecondaryConstructor.containingClassOrObject as? KtClass ?: return
            val parameterToPropertyMap =
                elementContext.parameterToPropertyMap.map { (parameterName, property) -> parameterName to updater.getWritable(property) }
                    .toMap()
            val psiFactory = KtPsiFactory(klass.project)
            val constructorCommentSaver = CommentSaver(secondaryConstructor)

            val constructor = psiFactory.createPrimaryConstructorWithModifiers(secondaryConstructor.modifierList?.text?.replace("\n", " "))

            writableSecondaryConstructor.moveParametersToPrimaryConstructorAndInitializers(
                constructor,
                parameterToPropertyMap,
                elementContext.parametersAsProperties,
                psiFactory
            )

            if (elementContext.classRefToUpdate >= 0) {
                val argumentList = secondaryConstructor.getDelegationCall().valueArgumentList?.text ?: "()"
                klass.superTypeListEntries.getOrNull(elementContext.classRefToUpdate)?.let { entry ->
                    val typeText = entry.typeReference?.text ?: return@let
                    val superTypeCallEntry = psiFactory.createSuperTypeCallEntry(
                        "$typeText$argumentList"
                    )
                    entry.replace(superTypeCallEntry)
                }
            }

            val replacedConstructor = klass.createPrimaryConstructorIfAbsent().replace(constructor)
            constructorCommentSaver.restore(replacedConstructor)

            val initializer = elementContext.initializer
            if ((initializer.body as? KtBlockExpression)?.statements?.isNotEmpty() == true) {
                if (elementContext.hasPropertyAfterInitializer) {
                    // In this case we must move init {} down, because it uses a property declared below
                    klass.addDeclaration(initializer)
                    writableSecondaryConstructor.delete()
                } else {
                    writableSecondaryConstructor.replace(initializer)
                }
            } else {
                writableSecondaryConstructor.delete()
            }
        }

        private fun KtSecondaryConstructor.moveParametersToPrimaryConstructorAndInitializers(
            primaryConstructor: KtPrimaryConstructor,
            parameterToPropertyMap: Map<String, KtProperty>,
            parametersToDelete: Set<String>,
            factory: KtPsiFactory
        ) {
            val parameterList = primaryConstructor.valueParameterList!!
            for (parameter in valueParameters) {
                val newParameter = factory.createParameter(parameter.text)
                val parameterName = parameter.name!!
                val property = parameterToPropertyMap[parameterName]
                var propertyCommentSaver: CommentSaver? = null
                if (property != null) {
                    if (parameterName in parametersToDelete) {
                        propertyCommentSaver = CommentSaver(property)
                        val valOrVar = if (property.isVar) factory.createVarKeyword() else factory.createValKeyword()
                        newParameter.addBefore(valOrVar, newParameter.nameIdentifier)
                        val propertyModifiers = property.modifierList?.text
                        if (propertyModifiers != null) {
                            val newModifiers = factory.createModifierList(propertyModifiers)
                            newParameter.addBefore(newModifiers, newParameter.valOrVarKeyword)
                        }
                        property.delete()
                    } else {
                        property.initializer = factory.createSimpleName(parameterName)
                    }
                }
                val addedParameter = parameterList.addParameter(newParameter)
                propertyCommentSaver?.restore(addedParameter)
            }
        }
    }
}