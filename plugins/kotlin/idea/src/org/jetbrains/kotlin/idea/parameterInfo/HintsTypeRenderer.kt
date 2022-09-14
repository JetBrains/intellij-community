// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.ClassifierNamePolicyEx
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.hints.InlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TextInlayInfoDetail
import org.jetbrains.kotlin.idea.codeInsight.hints.TypeInlayInfoDetail
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.error.*
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.isUnresolvedType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * copy-pasted and inspired by [DescriptorRendererImpl]
 *
 * To render any kotlin type into a sequence of short and human-readable kotlin types like
 * - Int
 * - Int?
 * - List<String?>?
 *
 * For each type short name and fqName is provided (see [TypeInlayInfoDetail]).
 */
class HintsTypeRenderer private constructor(override val options: HintsDescriptorRendererOptions) : KotlinIdeDescriptorRenderer(options) {

    init {
        check(options.isLocked) { "options have not been locked yet to prevent mutability" }
        check(options.textFormat == RenderingFormat.PLAIN) { "only PLAIN text format is supported" }
        check(!options.verbose) { "verbose mode is not supported" }
        check(!options.renderTypeExpansions) { "Type expansion rendering is unsupported" }
    }

    private val renderer = COMPACT_WITH_SHORT_TYPES.withOptions {}

    @Suppress("SuspiciousCollectionReassignment")
    private val functionTypeAnnotationsRenderer: HintsTypeRenderer by lazy {
        HintsTypeRenderer.withOptions {
            excludedTypeAnnotationClasses += listOf(StandardNames.FqNames.extensionFunctionType)
        }
    }

    private fun renderName(name: Name): String = escape(name.render())

    /* TYPES RENDERING */
    fun renderTypeIntoInlayInfo(type: KotlinType): List<InlayInfoDetail> {
        val list = mutableListOf<InlayInfoDetail>()
        return options.typeNormalizer.invoke(type).renderNormalizedTypeTo(list)
    }

    private fun MutableList<InlayInfoDetail>.append(text: String): MutableList<InlayInfoDetail> = apply {
        add(TextInlayInfoDetail(text))
    }

    private fun MutableList<InlayInfoDetail>.append(text: String, descriptor: ClassifierDescriptor?): MutableList<InlayInfoDetail> {
        descriptor?.let {
            add(TypeInlayInfoDetail(text, it.fqNameSafe.asString()))
        } ?: run {
            append(text)
        }
        return this
    }

    private fun KotlinType.renderNormalizedTypeTo(list: MutableList<InlayInfoDetail>): MutableList<InlayInfoDetail> {
        this.unwrap().safeAs<AbbreviatedType>()?.let { abbreviated ->
            // TODO nullability is lost for abbreviated type?
            abbreviated.abbreviation.renderNormalizedTypeAsIsTo(list)
            if (options.renderUnabbreviatedType) {
                abbreviated.renderAbbreviatedTypeExpansionTo(list)
            }
            return list
        }

        this.renderNormalizedTypeAsIsTo(list)
        return list
    }

    private fun AbbreviatedType.renderAbbreviatedTypeExpansionTo(list: MutableList<InlayInfoDetail>) {
        list.append(" /* = ")
        this.expandedType.renderNormalizedTypeAsIsTo(list)
        list.append(" */")
    }

    private fun KotlinType.renderNormalizedTypeAsIsTo(list:  MutableList<InlayInfoDetail>) {
        if (this is WrappedType && !this.isComputed()) {
            list.append("<Not computed yet>")
            return
        }
        when (val unwrappedType = this.unwrap()) {
            // KTIJ-19098: platform type (e.g. `String!`) is rendered like a plain text `String!` w/o fqName link
            is FlexibleType -> list.append(unwrappedType.render(renderer, options))
            is SimpleType -> unwrappedType.renderSimpleTypeTo(list)
        }
    }

    private fun SimpleType.renderSimpleTypeTo(list: MutableList<InlayInfoDetail>) {
        if (this == TypeUtils.CANNOT_INFER_FUNCTION_PARAM_TYPE || TypeUtils.isDontCarePlaceholder(this)) {
            list.append("???")
            return
        }
        if (ErrorUtils.isUninferredTypeVariable(this)) {
            if (options.uninferredTypeParameterAsName) {
                list.append(renderError((this.constructor as ErrorTypeConstructor).getParam(0)))
            } else {
                list.append("???")
            }
            return
        }

        if (this.isError) {
            this.renderDefaultTypeTo(list)
            return
        }
        if (shouldRenderAsPrettyFunctionType(this)) {
            this.renderFunctionTypeTo(list)
        } else {
            this.renderDefaultTypeTo(list)
        }
    }

    private fun List<TypeProjection>.renderTypeArgumentsTo(list: MutableList<InlayInfoDetail>) {
        if (this.isNotEmpty()) {
            list.append(lt())
            this.appendTypeProjectionsTo(list)
            list.append(gt())
        }
    }

    private fun KotlinType.renderDefaultTypeTo(list: MutableList<InlayInfoDetail>) {
        renderAnnotationsTo(list)

        if (this.isError) {
            if (isUnresolvedType(this) && options.presentableUnresolvedTypes) {
                list.append(ErrorUtils.unresolvedTypeAsItIs(this))
            } else {
                if (this is ErrorType && !options.informativeErrorType) {
                    list.append(this.debugMessage)
                } else {
                    list.append(this.constructor.toString()) // Debug name of an error type is more informative
                }
                this.arguments.renderTypeArgumentsTo(list)
            }
        } else {
            this.renderTypeConstructorAndArgumentsTo(list)
        }

        if (this.isMarkedNullable) {
            list.append("?")
        }

        if (classifierNamePolicy !is ClassifierNamePolicyEx) {
            if (this.isDefinitelyNotNullType) {
                list.append(" & Any")
            }
        }
    }

    private fun KotlinType.renderTypeConstructorAndArgumentsTo(
        list: MutableList<InlayInfoDetail>,
        typeConstructor: TypeConstructor = this.constructor
    ) {
        val possiblyInnerType = this.buildPossiblyInnerType()
        if (possiblyInnerType == null) {
            typeConstructor.renderTypeConstructorOfTypeTo(list, this)
            this.arguments.renderTypeArgumentsTo(list)
            return
        }

        possiblyInnerType.renderPossiblyInnerTypeTo(list)
    }

    private fun PossiblyInnerType.renderPossiblyInnerTypeTo(list: MutableList<InlayInfoDetail>) {
        this.outerType?.let {
            it.renderPossiblyInnerTypeTo(list)
            list.append(".")
            list.append(renderName(this.classifierDescriptor.name))
        } ?: this.classifierDescriptor.typeConstructor.renderTypeConstructorTo(list)

        this.arguments.renderTypeArgumentsTo(list)
    }

    private fun TypeConstructor.renderTypeConstructorOfTypeTo(list: MutableList<InlayInfoDetail>, type: KotlinType){
        val text = when (val cd = this.declarationDescriptor) {
            is TypeParameterDescriptor, is ClassDescriptor, is TypeAliasDescriptor -> renderClassifierNameWithType(cd, type)
            null -> this.toString()
            else -> error("Unexpected classifier: " + cd::class.java)
        }
        list.append(text, this.declarationDescriptor)
    }

    private fun TypeConstructor.renderTypeConstructorTo(list: MutableList<InlayInfoDetail>){
        val text = when (val cd = this.declarationDescriptor) {
            is TypeParameterDescriptor, is ClassDescriptor, is TypeAliasDescriptor -> renderClassifierName(cd)
            null -> this.toString()
            else -> error("Unexpected classifier: " + cd::class.java)
        }
        list.append(text, this.declarationDescriptor)
    }

    override fun renderClassifierName(klass: ClassifierDescriptor): String = if (ErrorUtils.isError(klass)) {
        klass.typeConstructor.toString()
    } else
        options.hintsClassifierNamePolicy.renderClassifier(klass, this)

    private fun KotlinType.renderFunctionTypeTo(list: MutableList<InlayInfoDetail>) {
        val type = this
        val lengthBefore = list.size
        // we need special renderer to skip @ExtensionFunctionType
        with(functionTypeAnnotationsRenderer) {
            type.renderAnnotationsTo(list)
        }
        val hasAnnotations = list.size != lengthBefore

        val isSuspend = this.isSuspendFunctionType
        val isNullable = this.isMarkedNullable
        val receiverType = this.getReceiverTypeFromFunctionType()

        val needParenthesis = isNullable || (hasAnnotations && receiverType != null)
        if (needParenthesis) {
            if (isSuspend) {
                list.add(lengthBefore, TextInlayInfoDetail("("))
            } else {
                if (hasAnnotations) {
                    if (list[list.lastIndex - 1].text != ")") {
                        // last annotation rendered without parenthesis - need to add them otherwise parsing will be incorrect
                        list.add(list.lastIndex, TextInlayInfoDetail("()"))
                    }
                }

                list.append("(")
            }
        }

        if (receiverType != null) {
            val surroundReceiver = shouldRenderAsPrettyFunctionType(receiverType) && !receiverType.isMarkedNullable ||
                    receiverType.hasModifiersOrAnnotations()
            if (surroundReceiver) {
                list.append("(")
            }
            receiverType.renderNormalizedTypeTo(list)
            if (surroundReceiver) {
                list.append(")")
            }
            list.append(".")
        }

        list.append("(")

        val parameterTypes = this.getValueParameterTypesFromFunctionType()
        for ((index, typeProjection) in parameterTypes.withIndex()) {
            if (index > 0) list.append(", ")

            if (options.parameterNamesInFunctionalTypes) {
                typeProjection.type.extractParameterNameFromFunctionTypeArgument()?.let { name ->
                    list.append(renderName(name))
                    list.append(": ")
                }
            }

            typeProjection.renderTypeProjectionTo(list)
        }

        list.append(") ").append(arrow()).append(" ")
        this.getReturnTypeFromFunctionType().renderNormalizedTypeTo(list)

        if (needParenthesis) list.append(")")

        if (isNullable) list.append("?")
    }

    private fun TypeProjection.renderTypeProjectionTo(list: MutableList<InlayInfoDetail>) =
        listOf(this).appendTypeProjectionsTo(list)

    private fun List<TypeProjection>.appendTypeProjectionsTo(list: MutableList<InlayInfoDetail>) {
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()

            if (next.isStarProjection) {
                list.append("*")
            } else {
                val renderedType = renderTypeIntoInlayInfo(next.type)
                if (next.projectionKind != Variance.INVARIANT) {
                    list.append("${next.projectionKind} ")
                }
                list.addAll(renderedType)
            }

            if (iterator.hasNext()) {
                list.append(", ")
            }
        }
    }

    private fun Annotated.renderAnnotationsTo(list: MutableList<InlayInfoDetail>, target: AnnotationUseSiteTarget? = null) {
        if (DescriptorRendererModifier.ANNOTATIONS !in options.modifiers) return

        val excluded = if (this is KotlinType) options.excludedTypeAnnotationClasses else options.excludedAnnotationClasses

        val annotationFilter = options.annotationFilter
        for (annotation in this.annotations) {
            if (annotation.fqName !in excluded
                && !annotation.isParameterName()
                && (annotationFilter == null || annotationFilter(annotation))
            ) {
                list.append(renderAnnotation(annotation, target))
                if (options.eachAnnotationOnNewLine) {
                    list.append("\n")
                } else {
                    list.append(" ")
                }
            }
        }
    }

    override fun renderAnnotation(annotation: AnnotationDescriptor, target: AnnotationUseSiteTarget?): String {
        return buildString {
            append('@')
            if (target != null) {
                append(target.renderName + ":")
            }
            val annotationType = annotation.type
            append(renderTypeIntoInlayInfo(annotationType))
        }
    }

    companion object {
        @JvmStatic
        private fun withOptions(changeOptions: HintsDescriptorRendererOptions.() -> Unit): HintsTypeRenderer {
            val options = HintsDescriptorRendererOptions()
            options.changeOptions()
            options.lock()
            return HintsTypeRenderer(options)
        }

        internal fun getInlayHintsTypeRenderer(bindingContext: BindingContext, context: KtElement) =
            withOptions {
                modifiers = emptySet()
                parameterNameRenderingPolicy = ParameterNameRenderingPolicy.ONLY_NON_SYNTHESIZED
                enhancedTypes = true
                textFormat = RenderingFormat.PLAIN
                renderUnabbreviatedType = false
                hintsClassifierNamePolicy = ImportAwareClassifierNamePolicy(bindingContext, context)
            }
    }

    internal class ImportAwareClassifierNamePolicy(
        val bindingContext: BindingContext,
        val context: KtElement
    ): HintsClassifierNamePolicy {
        override fun renderClassifier(classifier: ClassifierDescriptor, renderer: HintsTypeRenderer): String {
            if (classifier.containingDeclaration is ClassDescriptor) {
                val resolutionFacade = context.getResolutionFacade()
                val scope = context.getResolutionScope(bindingContext, resolutionFacade)
                if (scope.findClassifier(classifier.name, NoLookupLocation.FROM_IDE) == classifier) {
                    return classifier.name.asString()
                }
            }

            return shortNameWithCompanionNameSkip(classifier, renderer)
        }

        private fun shortNameWithCompanionNameSkip(classifier: ClassifierDescriptor, renderer: HintsTypeRenderer): String {
            if (classifier is TypeParameterDescriptor) return renderer.renderName(classifier.name)

            val qualifiedNameParts = classifier.parentsWithSelf
                .takeWhile { it is ClassifierDescriptor }
                .filter { !(it.isCompanionObject() && it.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) }
                .mapTo(ArrayList()) { it.name }
                .reversed()

            return renderFqName(qualifiedNameParts)
        }

    }
}