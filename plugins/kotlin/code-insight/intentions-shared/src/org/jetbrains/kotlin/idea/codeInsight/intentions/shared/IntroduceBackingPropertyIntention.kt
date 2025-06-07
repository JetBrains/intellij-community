// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.getTypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateTypeForDeclarationInDummyFile
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.hasJvmFieldAnnotation
import org.jetbrains.kotlin.idea.util.isBackingFieldRequired
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

class IntroduceBackingPropertyIntention :
    KotlinApplicableModCommandAction<KtProperty, IntroduceBackingPropertyIntention.Context>(KtProperty::class) {
    class Context(
        val typeInfo: TypeInfo,
        val getterFieldReferences: List<SmartPsiElementPointer<KtSimpleNameExpression>>,
        val setterFieldReferences: List<SmartPsiElementPointer<KtSimpleNameExpression>>,
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Context,
        updater: ModPsiUpdater
    ) {
        introduceBackingProperty(element, elementContext, updater)
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("introduce.backing.property")

    override fun KaSession.prepareContext(element: KtProperty): Context? {
        val name = element.name ?: return null
        if (!isBackingFieldRequiredAndCanBeUsed(element)) return null

        val containingClass = element.getStrictParentOfType<KtClassOrObject>() ?: return null
        if (containingClass.isExpectDeclaration()) return null
        return if (containingClass.declarations.none { it is KtProperty && it.name == "_$name" }) {
            val getterFieldReferences = element.getter?.let { collectFieldReferences(it) } ?: emptyList()
            val setterFieldReferences = if (element.isVar) {
                element.setter?.let { collectFieldReferences(it) } ?: emptyList()
            } else {
                emptyList()
            }
            Context(getTypeInfo(element), getterFieldReferences, setterFieldReferences)
        } else {
            null
        }
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        element.name ?: return false
        if (element.hasModifier(KtTokens.CONST_KEYWORD)) return false
        return !element.hasJvmFieldAnnotation()
    }

    private fun KaSession.isBackingFieldRequiredAndCanBeUsed(property: KtProperty): Boolean {
        if (property.isAbstract()) return false

        if (property.isLocal) return false

        if (isBackingFieldRequired(property)) return true

        val getter = property.getter
        return property.isVar || getter?.bodyExpression?.evaluate() == null
    }

    private fun introduceBackingProperty(element: KtProperty, context: Context, updater: ModPsiUpdater) {
        createBackingProperty(element)

        val project = element.project
        replaceFieldReferences(project, element.name!!, context, updater)

        val copiedProperty = element.copied()
        addAccessors(copiedProperty)
        removeInitializer(project, copiedProperty, context)

        val replaced = element.replaced(copiedProperty)
        replaced.typeReference?.let { ShortenReferencesFacility.getInstance().shorten(it) }
        replaced.reformat()
    }

    private fun replaceFieldReferences(
        project: Project, propertyName: String, context: Context, updater: ModPsiUpdater
    ) {
        fun replaceFieldReference(reference: SmartPsiElementPointer<KtSimpleNameExpression>) {
            val writableElement = reference.element?.let { updater.getWritable(it) } ?: return
            writableElement.replace(KtPsiFactory(project).createSimpleName("_$propertyName"))
        }
        for (getterFieldReference in context.getterFieldReferences) {
            replaceFieldReference(getterFieldReference)
        }
        for (setterFieldReference in context.setterFieldReferences) {
            replaceFieldReference(setterFieldReference)
        }
    }

    private fun removeInitializer(project: Project, property: KtProperty, context: Context) {
        property.removeModifier(KtTokens.LATEINIT_KEYWORD)
        if (property.typeReference == null) {
            updateTypeForDeclarationInDummyFile(property, context.typeInfo, project)
        }
        property.initializer = null
    }

    private fun addAccessors(property: KtProperty) {
        val getter = property.getter
        if (getter == null) {
            createGetter(property)
        }

        if (property.isVar) {
            val setter = property.setter
            if (setter == null) {
                createSetter(property)
            }
        }
    }

    private fun createGetter(element: KtProperty) {
        val body = "get() = ${backingName(element)}"
        val newGetter = KtPsiFactory(element.project).createProperty("val x $body").getter!!
        element.addAccessor(newGetter)
    }

    private fun createSetter(element: KtProperty) {
        val body = "set(value) { ${backingName(element)} = value }"
        val newSetter = KtPsiFactory(element.project).createProperty("val x $body").setter!!
        element.addAccessor(newSetter)
    }

    private fun KtProperty.addAccessor(newAccessor: KtPropertyAccessor) {
        val semicolon = node.findChildByType(KtTokens.SEMICOLON)
        addBefore(newAccessor, semicolon?.psi)
    }

    private fun createBackingProperty(property: KtProperty) {
        val backingProperty = KtPsiFactory(property.project).buildDeclaration {
            appendFixedText("private ")
            appendFixedText(property.valOrVarKeyword.text)
            appendFixedText(" ")
            appendNonFormattedText(backingName(property))
            if (property.typeReference != null) {
                appendFixedText(": ")
                appendTypeReference(property.typeReference)
            }
            if (property.initializer != null) {
                appendFixedText(" = ")
                appendExpression(property.initializer)
            }
        }

        if (property.hasModifier(KtTokens.LATEINIT_KEYWORD)) {
            backingProperty.addModifier(KtTokens.LATEINIT_KEYWORD)
        }

        property.parent.addBefore(backingProperty, property)
    }

    private fun backingName(property: KtProperty): String {
        return if (property.nameIdentifier?.text?.startsWith('`') == true) "`_${property.name}`" else "_${property.name}"
    }

    private fun KaSession.collectFieldReferences(element: KtElement): List<SmartPsiElementPointer<KtSimpleNameExpression>> {
        val fieldReferences = mutableListOf<SmartPsiElementPointer<KtSimpleNameExpression>>()
        element.acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val variableSymbol = expression.mainReference.resolveToSymbol()
                if (variableSymbol is KaBackingFieldSymbol) {
                    fieldReferences.add(expression.createSmartPointer())
                }
            }
        })
        return fieldReferences
    }
}
