// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.getDefaultInitializer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError

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
                        kotlinStyleDeclareOut(container, dummyFirstStatement, resultStatements, propertiesDeclarations, property)
                    } else {
                        declareOut(
                            container,
                            dummyFirstStatement,
                            generateDefaultInitializers,
                            resultStatements,
                            propertiesDeclarations,
                            property
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

    ShortenReferences.DEFAULT.process(propertiesDeclarations)

    return PsiUtilCore.toPsiElementArray(resultStatements)
}

private fun kotlinStyleDeclareOut(
    container: PsiElement,
    dummyFirstStatement: PsiElement,
    resultStatements: ArrayList<PsiElement>,
    propertiesDeclarations: ArrayList<KtProperty>,
    property: KtProperty
) {
    val name = property.name ?: return
    val psiFactory = KtPsiFactory(property.project)
    var declaration = psiFactory.createProperty(name, property.typeReference?.text, property.isVar, null)
    declaration = container.addBefore(declaration, dummyFirstStatement) as KtProperty
    container.addAfter(psiFactory.createEQ(), declaration)
    propertiesDeclarations.add(declaration)
    property.initializer?.let {
        resultStatements.add(property.replace(it))
    }
}

private fun declareOut(
    container: PsiElement,
    dummyFirstStatement: PsiElement,
    generateDefaultInitializers: Boolean,
    resultStatements: ArrayList<PsiElement>,
    propertiesDeclarations: ArrayList<KtProperty>,
    property: KtProperty
) {
    var declaration = createVariableDeclaration(property, generateDefaultInitializers)
    declaration = container.addBefore(declaration, dummyFirstStatement) as KtProperty
    propertiesDeclarations.add(declaration)
    val assignment = createVariableAssignment(property)
    resultStatements.add(property.replace(assignment))
}

private fun createVariableAssignment(property: KtProperty): KtBinaryExpression {
    val propertyName = property.name ?: error("Property should have a name " + property.text)
    val assignment = KtPsiFactory(property.project).createExpression("$propertyName = x") as KtBinaryExpression
    val right = assignment.right ?: error("Created binary expression should have a right part " + assignment.text)
    val initializer = property.initializer ?: error("Initializer should exist for property " + property.text)
    right.replace(initializer)
    return assignment
}

private fun createVariableDeclaration(property: KtProperty, generateDefaultInitializers: Boolean): KtProperty {
    val propertyType = getPropertyType(property)
    var defaultInitializer: String? = null
    if (generateDefaultInitializers && property.isVar) {
        defaultInitializer = propertyType.getDefaultInitializer()
    }
    return createProperty(property, propertyType, defaultInitializer)
}

private fun getPropertyType(property: KtProperty): KotlinType {
    val variableDescriptor = property.resolveToDescriptorIfAny(BodyResolveMode.PARTIAL)
        ?: error("Couldn't resolve property to property descriptor " + property.text)
    return variableDescriptor.type
}

private fun createProperty(property: KtProperty, propertyType: KotlinType, initializer: String?): KtProperty {
    val typeRef = property.typeReference
    val typeString = when {
        typeRef != null -> typeRef.text
        !propertyType.isError -> IdeDescriptorRenderers.SOURCE_CODE.renderType(propertyType)
        else -> null
    }

    return KtPsiFactory(property.project).createProperty(property.name!!, typeString, property.isVar, initializer)
}

private fun needToDeclareOut(element: PsiElement, lastStatementOffset: Int, scope: SearchScope): Boolean {
    if (element is KtProperty ||
        element is KtClassOrObject ||
        element is KtFunction
    ) {
        val refs = ReferencesSearch.search(element, scope, false).toArray(PsiReference.EMPTY_ARRAY)
        if (refs.isNotEmpty()) {
            val lastRef = refs.maxByOrNull { it.element.textOffset } ?: return false
            if (lastRef.element.textOffset > lastStatementOffset) {
                return true
            }
        }
    }

    return false
}
