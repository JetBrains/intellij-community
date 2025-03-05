// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

fun move(container: PsiElement, statements: Array<PsiElement>, generateDefaultInitializers: Boolean): Array<PsiElement> {
    if (statements.isEmpty()) {
        return statements
    }

    val project = container.project

    val resultStatements = ArrayList<PsiElement>()
    val propertiesDeclarations = ArrayList<KtProperty>()

    // Dummy element to add new declarations at the beginning
    val psiFactory = KtPsiFactory(project)
    val dummyFirstStatement = container.addBefore(psiFactory.createExpression("dummyStatement"), statements[0])

    try {
        val scope = LocalSearchScope(container)
        val lastStatementOffset = statements[statements.size - 1].textRange.endOffset


        statements.forEachIndexed { i, statement ->
            if (needToDeclareOut(statement, lastStatementOffset, scope)) {
                val property = statement as? KtProperty
                if (property?.initializer != null) {
                    if (i == statements.size - 1) {
                        prepareLastPropertyToBeInitializedWithExpression(
                            psiFactory,
                            container, dummyFirstStatement, resultStatements, propertiesDeclarations, property
                        )
                    } else {
                        declareOut(
                            psiFactory,
                            container, dummyFirstStatement, generateDefaultInitializers, resultStatements, propertiesDeclarations, property
                        )
                    }
                } else {
                    val newStatement = container.addBefore(statement, dummyFirstStatement)
                    container.addAfter(psiFactory.createNewLine(), newStatement)
                    container.deleteChildRange(statement, statement)
                }
            } else {
                resultStatements.add(statement)
            }
        }
    } finally {
        dummyFirstStatement.delete()
    }

    val shortenReferencesFacility = ShortenReferencesFacility.getInstance()
    for (ktProperty in propertiesDeclarations) {
        shortenReferencesFacility.shorten(ktProperty)
    }

    return PsiUtilCore.toPsiElementArray(resultStatements)
}

/**
 * As a result: `property_initializerval property=`; should be fixed on the call site of [move] method
 */
private fun prepareLastPropertyToBeInitializedWithExpression(
    psiFactory: KtPsiFactory,
    container: PsiElement,
    dummyFirstStatement: PsiElement,
    resultStatements: ArrayList<PsiElement>,
    propertiesDeclarations: ArrayList<KtProperty>,
    property: KtProperty
) {
    val name = property.name ?: return
    var declaration = psiFactory.createProperty(name, property.typeReference?.text, property.isVar, null)
    declaration = container.addBefore(declaration, dummyFirstStatement) as KtProperty
    container.addAfter(psiFactory.createEQ(), declaration)
    propertiesDeclarations.add(declaration)
    property.initializer?.let {
        resultStatements.add(property.replace(it))
    }
}

private fun declareOut(
    psiFactory: KtPsiFactory,
    container: PsiElement,
    dummyFirstStatement: PsiElement,
    generateDefaultInitializers: Boolean,
    resultStatements: ArrayList<PsiElement>,
    propertiesDeclarations: ArrayList<KtProperty>,
    property: KtProperty
) {
    var declaration = createVariableDeclaration(psiFactory, property, generateDefaultInitializers)
    declaration = container.addBefore(declaration, dummyFirstStatement) as KtProperty
    container.addBefore(psiFactory.createNewLine(), dummyFirstStatement)
    propertiesDeclarations.add(declaration)
    val assignment = createVariableAssignment(psiFactory, property)
    resultStatements.add(property.replace(assignment))
}

private fun createVariableAssignment(psiFactory: KtPsiFactory, property: KtProperty): KtBinaryExpression {
    val propertyName = property.name ?: error("Property should have a name " + property.text)
    val assignment = psiFactory.createExpression("$propertyName = x") as KtBinaryExpression
    val right = assignment.right ?: error("Created binary expression should have a right part " + assignment.text)
    val initializer = property.initializer ?: error("Initializer should exist for property " + property.text)
    right.replace(initializer)
    return assignment
}

@OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
private fun createVariableDeclaration(psiFactory: KtPsiFactory, property: KtProperty, generateDefaultInitializers: Boolean): KtProperty {
    allowAnalysisOnEdt {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(property) {
                val propertyType = property.returnType

                var defaultInitializer: String? = null
                if (generateDefaultInitializers && property.isVar) {
                    defaultInitializer = propertyType.defaultInitializer

                }
                val typeRef = property.typeReference
                val typeString = when {
                    typeRef != null -> typeRef.text
                    propertyType !is KaErrorType -> propertyType.render(
                        KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.typeRenderer, Variance.INVARIANT
                    )

                    else -> null
                }

                return psiFactory.createProperty(property.name!!, typeString, property.isVar, defaultInitializer)
            }
        }
    }
}

private fun needToDeclareOut(element: PsiElement, lastStatementOffset: Int, scope: SearchScope): Boolean {
    if (element is KtProperty || element is KtClassOrObject || element is KtFunction) {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        val refs = allowAnalysisFromWriteAction { ReferencesSearch.search(element, scope, false).toArray(PsiReference.EMPTY_ARRAY) }
        if (refs.isNotEmpty()) {
            val lastRef = refs.maxByOrNull { it.element.textOffset } ?: return false
            if (lastRef.element.textOffset > lastStatementOffset) {
                return true
            }
        }
    }

    return false
}
