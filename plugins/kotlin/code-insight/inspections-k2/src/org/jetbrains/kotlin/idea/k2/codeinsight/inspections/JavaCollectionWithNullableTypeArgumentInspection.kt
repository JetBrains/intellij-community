// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jdom.Element
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.isNullable
import org.jetbrains.kotlin.analysis.api.components.lowerBoundIfFlexible
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.typeIfSafeToResolve
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

/**
 * This inspection process:
 * 1. Parameterized constructor calls like `val map = ConcurrentHashMap<T, String?>()` – process both generics and explicitly nullable types.
 * 2. Properties with type declarations like `val queue: PriorityQueue<String?> = PriorityQueue<String?>()`.
 * 3. Constructors with arguments like `val map = ConcurrentHashMap(someMap)`.
 * 4. Typealiases like
 * ```
 * typealias Foo = String?
 * val deque = ConcurrentLinkedDeque<Foo>()
 * ```
 */
internal class JavaCollectionWithNullableTypeArgumentInspection :
    KotlinApplicableInspectionBase<KtElement, JavaCollectionWithNullableTypeArgumentInspection.Context>() {

    internal class Context(
        val collectionName: Name,
        val canMakeNonNullable: Boolean,
        // Can contain both explicitly and implicitly nullable type arguments
        val nullableTypeArguments: List<SmartPsiElementPointer<KtTypeProjection>>
    )

    override fun isApplicableByPsi(element: KtElement): Boolean = true

    override fun InspectionManager.createProblemDescriptor(
        element: KtElement,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val fixes = if (context.canMakeNonNullable) {
            val fix = LocalQuickFix.from(RemoveNullabilityModCommandAction())
            arrayOf(fix!!)
        } else {
            emptyArray()
        }

        val nullableTypeArguments = context.nullableTypeArguments.mapNotNull { it.dereference() }
        val rangeInElement =
            (ApplicabilityRange.union(element) { nullableTypeArguments }).singleOrNull()
                ?: ApplicabilityRange.self(element).singleOrNull()
                ?: errorWithAttachment("No range found") { withPsiEntry("KtElement", element) }

        val description = KotlinBundle.message(
            "java.collection.is.parameterized.with.nullable.type",
            context.collectionName.asString(),
            if (nullableTypeArguments.size > 1) 2 else 1
        )

        return createProblemDescriptor(
            /* psiElement = */
            element,
            /* rangeInElement = */
            rangeInElement,
            /* descriptionTemplate = */
            description,
            /* highlightType = */
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */
            onTheFly,
            /* ...fixes = */
            *fixes,
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitTypeReference(typeReference: KtTypeReference) {
            visitTargetElement(typeReference, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtElement): Context? {
        val typeArguments = element.getTypeArguments() ?: return null

        val canMakeNonNullable: Boolean
        val collectionName: FqName
        when (element) {
            is KtCallExpression -> {
                val constructorCall = element.resolveToCall()?.successfulConstructorCallOrNull() ?: return null
                collectionName = constructorCall.symbol.importableFqName ?: return null

                if (typeArguments.isEmpty()) {
                    if (!constructorHasNullableParameters(constructorCall)) return null
                    canMakeNonNullable = false
                } else {
                    canMakeNonNullable = canMakeNonNullable(typeArguments) ?: return null
                }
            }

            is KtTypeReference -> {
                collectionName = element.typeIfSafeToResolve?.symbol?.importableFqName ?: return null
                canMakeNonNullable = canMakeNonNullable(typeArguments) ?: return null
            }

            else -> return null
        }

        return if (collectionName.toUnsafe().isNonNullableParameterizedJavaCollection(element)) {
            val nullableTypeArguments = typeArguments.filter { it.isExplicitlyNullable() || it.isImplicitlyNullable() }
            Context(collectionName = collectionName.shortName(), canMakeNonNullable, nullableTypeArguments.map { it.createSmartPointer() })
        } else {
            null
        }
    }

    /**
     * Returns `null` if we should stop processing.
     */
    private fun KaSession.canMakeNonNullable(typeArguments: List<KtTypeProjection>): Boolean? {
        return if (typeArguments.none { it.isExplicitlyNullable() }) {
            val implicitlyNullableTypes = typeArguments.filter { it.isImplicitlyNullable() }
            if (implicitlyNullableTypes.isEmpty()) return null
            implicitlyNullableTypes.any { !it.isTypeAlias() }
        } else {
            true
        }
    }

    private fun KaSession.constructorHasNullableParameters(constructorCall: KaFunctionCall<KaConstructorSymbol>): Boolean {
        val typeArgumentsMapping = constructorCall.typeArgumentsMapping
        if (typeArgumentsMapping.isEmpty()) return false
        val hasNullableTypes = typeArgumentsMapping.any { (_, typeArgument) ->
            typeArgument.isMarkedNullable
        }
        return hasNullableTypes
    }

    private fun FqNameUnsafe.isNonNullableParameterizedJavaCollection(element: KtElement): Boolean {
        val collectionsWithNonNullableTypeParameters = NonNullableParameterizedJavaCollectionsService.getCollections(element)
        return this in collectionsWithNonNullableTypeParameters
    }

    private class RemoveNullabilityModCommandAction :
        KotlinApplicableModCommandAction<KtElement, RemoveNullabilityModCommandAction.Context>(elementClass = KtElement::class) {

        data class Context(
            val typesToMakeNonNullable: Map<SmartPsiElementPointer<KtTypeProjection>, RemoveNullabilityAction>
        )

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.nullable.type.with.non.nullable.family.name")

        override fun invoke(
            actionContext: ActionContext,
            element: KtElement,
            elementContext: Context,
            updater: ModPsiUpdater
        ) {
            for ((typeArgument, action) in elementContext.typesToMakeNonNullable) {
                when (action) {
                    RemoveNullabilityAction.REMOVE_QUESTION_MARK -> typeArgument.dereference()?.removeQuestionMark()
                    RemoveNullabilityAction.MAKE_DEFINITELY_NON_NULLABLE -> typeArgument.dereference()?.makeDefinitelyNonNullable()
                }
            }
        }

        private fun KtTypeProjection.removeQuestionMark() {
            val typeElement = this.typeReference?.typeElement
            val unwrappedTypeElement = typeElement?.unwrapNullability() ?: return
            typeElement.replace(unwrappedTypeElement)
        }

        private fun KtTypeProjection.makeDefinitelyNonNullable() {
            val initialType = this.typeReference?.typeElement ?: return
            val definitelyNonNullableType = KtPsiFactory(initialType.project).createType("${initialType.text} & Any")
            initialType.replace(definitelyNonNullableType)
        }

        override fun KaSession.prepareContext(element: KtElement): Context {
            val typeArguments = element.getTypeArguments() ?: return Context(typesToMakeNonNullable = emptyMap())

            val typesAndRemoveNullabilityActions = mutableMapOf<SmartPsiElementPointer<KtTypeProjection>, RemoveNullabilityAction>()
            typeArguments.forEach {
                typesAndRemoveNullabilityActions[it.createSmartPointer()] = when {
                    it.isExplicitlyNullable() -> RemoveNullabilityAction.REMOVE_QUESTION_MARK
                    // Something that is implicitly nullable and is not a type alias is a type parameter
                    it.isImplicitlyNullable() && !it.isTypeAlias() -> RemoveNullabilityAction.MAKE_DEFINITELY_NON_NULLABLE
                    else -> return@forEach
                }
            }

            return Context(typesToMakeNonNullable = typesAndRemoveNullabilityActions)
        }

        private enum class RemoveNullabilityAction {
            REMOVE_QUESTION_MARK,
            MAKE_DEFINITELY_NON_NULLABLE,
        }
    }

    val nonNullableParameterizedJavaCollections: MutableSet<FqNameUnsafe> =
        NonNullableParameterizedJavaCollectionsService.DEFAULT.map(::FqNameUnsafe).toMutableSet()

    /**
     * Serialized setting to store collection names.
     * Declared public to provide reflection access from [getOptionsPane].
     */
    var fqNameCollectionsNamesStrings: MutableList<String> =
        NonNullableParameterizedJavaCollectionsService.DEFAULT.toMutableList()

    override fun readSettings(node: Element) {
        super.readSettings(node)
        readSettings()
    }

    override fun writeSettings(node: Element) {
        writeSettings()
        super.writeSettings(node)
    }

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.stringList(::fqNameCollectionsNamesStrings.name, KotlinBundle.message("java.collections.to.process")),
    )

    override fun getOptionController(): OptionController {
        return super.getOptionController()
            .onValueSet(::fqNameCollectionsNamesStrings.name) {
                readSettings()
            }
    }

    private fun readSettings() {
        nonNullableParameterizedJavaCollections.clear()
        fqNameCollectionsNamesStrings.mapTo(nonNullableParameterizedJavaCollections, ::FqNameUnsafe)
    }

    private fun writeSettings() {
        fqNameCollectionsNamesStrings.clear()
        nonNullableParameterizedJavaCollections.mapTo(fqNameCollectionsNamesStrings) { it.asString() }
    }
}

private fun KtTypeProjection.isExplicitlyNullable(): Boolean {
    return this.typeReference?.typeElement is KtNullableType
}

/**
 * Checks if a type is implicitly nullable.
 *
 * We can't use just `typeArgument.typeReference?.type?.canBeNull == true` at the call site.
 * We have to resolve to symbol because we need to get rid of flexible types in cases of constructors with type arguments.
 * This behavior of constructor type arguments being flexible is going to be changed in Kotlin 2.3 (KT-71718),
 * but we can't fix it here because we don't want different code for different language versions.
 */
context(_: KaSession)
private fun KtTypeProjection.isImplicitlyNullable(): Boolean {
    val userType = typeReference?.typeElement as? KtUserType ?: return false
    val symbol = userType.referenceExpression?.mainReference?.resolveToSymbol() as? KaClassifierSymbol ?: return false
    return symbol.defaultType.isNullable
}

context(_: KaSession)
private fun KtTypeProjection.isTypeAlias(): Boolean {
    val lowerBoundType = this.typeReference?.type?.lowerBoundIfFlexible()
    return lowerBoundType?.abbreviation?.symbol is KaTypeAliasSymbol
}

private fun KtElement.getTypeArguments(): List<KtTypeProjection>? {
    return when (this) {
        is KtTypeReference -> {
            val typeElement = this.typeElement
            val userType = typeElement?.unwrapNullability() as? KtUserType
            userType?.typeArguments.orEmpty()
        }

        is KtCallExpression -> {
            this.typeArguments
        }

        else -> null
    }
}
