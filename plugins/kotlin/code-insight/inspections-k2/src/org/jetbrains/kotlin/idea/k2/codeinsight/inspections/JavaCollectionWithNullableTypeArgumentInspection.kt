// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jdom.Element
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.typeArguments
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

/**
 * This inspection process:
 * 1. Parameterized constructor calls like `val map = ConcurrentHashMap<String, String>()`.
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

    internal class Context(val collectionName: Name, val canMakeNonNullable: Boolean)

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

        return createProblemDescriptor(
            /* psiElement = */
            element,
            /* rangeInElement = */
            rangeInElement,
            /* descriptionTemplate = */
            KotlinBundle.message("java.collection.is.parameterized.with.nullable.type", context.collectionName.asString()),
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

    override fun getApplicableRanges(element: KtElement): List<TextRange> {
        val typeArguments = element.getTypeArguments()
        val nullableTypeArguments = typeArguments?.filter { it.isExplicitlyNullable() }

        return if (nullableTypeArguments.isNullOrEmpty()) { // Can be implicitly nullable, for example, a type alias for a nullable
            ApplicabilityRange.self(element)
        } else {
            if (nullableTypeArguments.size == typeArguments.size) {
                ApplicabilityRange.union(element) { nullableTypeArguments }
            } else {
                ApplicabilityRange.multiple(element) { nullableTypeArguments }
            }
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
                collectionName = element.type.symbol?.importableFqName ?: return null
                canMakeNonNullable = canMakeNonNullable(typeArguments) ?: return null
            }

            else -> return null
        }

        return if (collectionName.toUnsafe().isNonNullableParameterizedJavaCollection(element)) {
            Context(collectionName = collectionName.shortName(), canMakeNonNullable = canMakeNonNullable)
        } else {
            null
        }
    }

    /**
     * Returns `null` if we should stop processing.
     */
    private fun KaSession.canMakeNonNullable(typeArguments: List<KtTypeProjection>): Boolean? {
        return if (typeArguments.none { it.isExplicitlyNullable() }) {
            if (typeArguments.none { it.isImplicitlyNullable() }) return null
            false
        } else {
            true
        }
    }

    private fun constructorHasNullableParameters(constructorCall: KaFunctionCall<KaConstructorSymbol>): Boolean {
        val typeArgumentsMapping = constructorCall.typeArgumentsMapping
        if (typeArgumentsMapping.isEmpty()) return false
        val hasNullableTypes = typeArgumentsMapping.any { (_, typeArgument) ->
            typeArgument.nullability.isNullable
        }
        return hasNullableTypes
    }

    private fun FqNameUnsafe.isNonNullableParameterizedJavaCollection(element: KtElement): Boolean {
        val collectionsWithNonNullableTypeParameters = NonNullableParameterizedJavaCollectionsService.getCollections(element)
        return this in collectionsWithNonNullableTypeParameters
    }

    private class RemoveNullabilityModCommandAction() :
        KotlinApplicableModCommandAction<KtElement, Unit>(elementClass = KtElement::class) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.nullable.type.with.non.nullable.family.name")

        override fun invoke(
            actionContext: ActionContext,
            element: KtElement,
            elementContext: Unit,
            updater: ModPsiUpdater
        ) {
            val typeArguments = element.getTypeArguments() ?: return
            typeArguments.forEach {
                removeNullability(it)
            }
        }

        private fun removeNullability(typeProjection: KtTypeProjection) {
            val initialNullableType = typeProjection.typeReference?.typeElement as? KtNullableType ?: return
            val deepestNullableType = generateSequence(initialNullableType) { it.innerType as? KtNullableType }.last()
            val innerType = deepestNullableType.innerType ?: return
            innerType.let { initialNullableType.replace(innerType) }
        }

        override fun KaSession.prepareContext(element: KtElement) {}
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
 * We can't use just
 * `typeArgument.typeReference?.type?.canBeNull == true`
 *  at the call site.
 *  We have to resolve to symbol because we need to get rid of flexible types in cases of constructors with type arguments.
 *  This behavior of constructor type arguments being flexible is going to be changed in Kotlin 2.3 (KT-71718),
 *  but we can't fix it here because we don't want different code for different language versions.
 */
context(KaSession)
private fun KtTypeProjection.isImplicitlyNullable(): Boolean {
    val userType = typeReference?.typeElement as? KtUserType ?: return false
    val symbol = userType.referenceExpression?.mainReference?.resolveToSymbol() as? KaClassifierSymbol ?: return false
    return symbol.defaultType.canBeNull
}

private fun KtElement.getTypeArguments(): List<KtTypeProjection>? {
    return when (this) {
        is KtTypeReference -> {
            this.typeArguments()
        }

        is KtCallExpression -> {
            this.typeArguments
        }

        else -> null
    }
}