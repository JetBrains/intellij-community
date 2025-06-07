// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.THIS_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.VARARG_KEYWORD
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiFactory.CallableBuilder
import org.jetbrains.kotlin.psi.KtPsiFactory.CallableBuilder.Target.CONSTRUCTOR
import org.jetbrains.kotlin.psi.psiUtil.*

class ConvertPrimaryConstructorToSecondaryIntention :
    KotlinApplicableModCommandAction<KtClass, List<ConvertPrimaryConstructorToSecondaryIntention.Item>>(
        KtClass::class,
    ) {

    data class Item(val propertyName: String, val initializer: String, val typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo?)

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.secondary.constructor")

    override fun getApplicableRanges(element: KtClass): List<TextRange> {
        val primaryCtor = element.primaryConstructor ?: return emptyList()
        val startOffset =
            (if (primaryCtor.getConstructorKeyword() != null) primaryCtor else element.nameIdentifier)?.startOffsetInParent ?: return emptyList()
        if (element.mustHaveOnlyPropertiesInPrimaryConstructor() || element.superTypeListEntries.any { it is KtDelegatedSuperTypeEntry }) {
            return emptyList()
        }
        if (primaryCtor.valueParameters.any { it.hasValOrVar() && (it.name == null || it.annotationEntries.isNotEmpty()) }) return emptyList()
        return listOf(TextRange(startOffset, primaryCtor.endOffset - element.textRange.startOffset))
    }

    override fun isApplicableByPsi(element: KtClass): Boolean {
        if (element.primaryConstructor == null) return false
        if (element.isAnnotation()) return false
        return true
    }

    override fun KaSession.prepareContext(element: KtClass): List<Item> {
        val items = mutableListOf<Item>()
        for (property in element.getProperties()) {
            val propertyName = property.name ?: continue
            if (property.isIndependent(element)) continue

            val initializer = property.initializer!!
            val returnType = property.returnType
            val typeInfo =
                if (property.typeReference == null && returnType !is KaErrorType) CallableReturnTypeUpdaterUtils.TypeInfo.createByKtTypes(
                    returnType
                ) else null
            items.add(Item(propertyName, initializer.text, typeInfo))
        }
        return items
    }

    private fun KtProperty.isIndependent(klass: KtClass): Boolean {
        val propertyInitializer = initializer ?: return true
        return !propertyInitializer.anyDescendantOfType<KtReferenceExpression> {
            when (val target = it.mainReference.resolve()) {
                null -> true

                is KtParameter -> target.ownerFunction?.containingClass() == klass

                else -> klass in target.parents
            }
        }
    }

    override fun invoke(
        actionContext: ActionContext, element: KtClass, elementContext: List<Item>, updater: ModPsiUpdater
    ) {
        val primaryCtor = element.primaryConstructor ?: return
        if (element.isAnnotation()) return
        val psiFactory = KtPsiFactory(element.project)
        val commentSaver = CommentSaver(primaryCtor)
        val initializerMap = mutableMapOf<KtProperty, String>()
        for (property in element.getProperties()) {
            val item = elementContext.firstOrNull { it.propertyName == property.name } ?: continue
            item.typeInfo?.let {
                updateType(property, it, property.project, updater)
            }
            val initializer = property.initializer!!
            initializerMap[property] = initializer.text
            initializer.delete()
            property.equalsToken!!.delete()
        }
        val constructor = psiFactory.createSecondaryConstructor(
            CallableBuilder(CONSTRUCTOR).apply {
                primaryCtor.modifierList?.let { modifier(it.text) }
                typeParams()
                name()
                for (valueParameter in primaryCtor.valueParameters) {
                    val annotations = valueParameter.annotationEntries.joinToString(separator = " ") { it.text }
                    val vararg = if (valueParameter.isVarArg) VARARG_KEYWORD.value else ""
                    param(
                        "$annotations $vararg ${valueParameter.name ?: ""}",
                        valueParameter.typeReference?.text ?: "",
                        valueParameter.defaultValue?.text
                    )
                }
                noReturnType()
                for (superTypeEntry in element.superTypeListEntries) {
                    if (superTypeEntry is KtSuperTypeCallEntry) {
                        superDelegation(superTypeEntry.valueArgumentList?.text ?: "")
                        superTypeEntry.replace(psiFactory.createSuperTypeEntry(superTypeEntry.typeReference!!.text))
                    }
                }
                val valueParameterInitializers =
                    primaryCtor.valueParameters.asSequence().filter { it.hasValOrVar() }.joinToString(separator = "\n") {
                        val name = it.name!!
                        "this.$name = $name"
                    }
                val classBodyInitializers = element.declarations.asSequence().filter {
                    (it is KtProperty && initializerMap[it] != null) || it is KtAnonymousInitializer
                }.joinToString(separator = "\n") {
                    if (it is KtProperty) {
                        val name = it.name!!
                        val text = initializerMap[it]
                        if (text != null) {
                            "${THIS_KEYWORD.value}.$name = $text"
                        } else {
                            ""
                        }
                    } else {
                        ((it as KtAnonymousInitializer).body as? KtBlockExpression)?.statements?.joinToString(separator = "\n") { stmt ->
                            stmt.text
                        } ?: ""
                    }
                }
                val allInitializers = listOf(valueParameterInitializers, classBodyInitializers).filter(String::isNotEmpty)
                if (allInitializers.isNotEmpty()) {
                    blockBody(allInitializers.joinToString(separator = "\n"))
                }
            }.asString()
        )

        val lastEnumEntry = element.declarations.lastOrNull { it is KtEnumEntry } as? KtEnumEntry
        val secondaryConstructor =
            lastEnumEntry?.let { element.addDeclarationAfter(constructor, it) } ?: element.addDeclarationBefore(constructor, null)
        commentSaver.restore(secondaryConstructor)

        convertValueParametersToProperties(primaryCtor, element, psiFactory, lastEnumEntry)
        if (element.isEnum()) {
            addSemicolonIfNotExist(element, psiFactory, lastEnumEntry)
        }

        for (anonymousInitializer in element.getAnonymousInitializers()) {
            anonymousInitializer.delete()
        }
        primaryCtor.delete()
    }

    private fun convertValueParametersToProperties(
        element: KtPrimaryConstructor, klass: KtClass, factory: KtPsiFactory, anchorBefore: PsiElement?
    ) {
        for (valueParameter in element.valueParameters.reversed()) {
            if (!valueParameter.hasValOrVar()) continue
            val isVararg = valueParameter.hasModifier(VARARG_KEYWORD)
            valueParameter.removeModifier(VARARG_KEYWORD)
            val typeText = valueParameter.typeReference?.text
            val property = factory.createProperty(
                valueParameter.modifierList?.text,
                valueParameter.name!!,
                if (isVararg && typeText != null) "Array<out $typeText>" else typeText,
                valueParameter.isMutable,
                null
            )
            if (anchorBefore == null) klass.addDeclarationBefore(property, null) else klass.addDeclarationAfter(property, anchorBefore)
        }
    }

    private fun addSemicolonIfNotExist(klass: KtClass, factory: KtPsiFactory, lastEnumEntry: KtEnumEntry?) {
        if (lastEnumEntry == null) {
            klass.getOrCreateBody().let { it.addAfter(factory.createSemicolon(), it.lBrace) }
        } else if (lastEnumEntry.getChildrenOfType<LeafPsiElement>().none { it.elementType == KtTokens.SEMICOLON }) {
            lastEnumEntry.add(factory.createSemicolon())
        }
    }
}