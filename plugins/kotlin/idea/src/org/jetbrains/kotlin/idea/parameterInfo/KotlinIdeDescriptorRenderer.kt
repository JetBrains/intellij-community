// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRendererHighlightingManager.Companion.Attributes
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils.isCompanionObject
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.declaresOrInheritsDefaultValue
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.ErrorUtils.UninferredParameterTypeConstructor
import org.jetbrains.kotlin.types.TypeUtils.CANT_INFER_FUNCTION_PARAM_TYPE
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.util.*


open class KotlinIdeDescriptorRenderer(
    open val options: KotlinIdeDescriptorOptions
) : DescriptorRenderer(), DescriptorRendererOptions by options /* this gives access to options without qualifier */ {

    private var overriddenHighlightingManager: KotlinIdeDescriptorRendererHighlightingManager<Attributes>? = null
        get() = field ?: options.highlightingManager

    private inline fun <R> withNoHighlighting(action: () -> R): R {
        val old = overriddenHighlightingManager
        overriddenHighlightingManager = KotlinIdeDescriptorRendererHighlightingManager.NO_HIGHLIGHTING
        val result = action()
        overriddenHighlightingManager = old
        return result
    }

    fun withIdeOptions(changeOptions: KotlinIdeDescriptorOptions.() -> Unit): KotlinIdeDescriptorRenderer {
        val options = this.options.copy()
        options.changeOptions()
        options.lock()
        return KotlinIdeDescriptorRenderer(options)
    }

    companion object {
        fun withOptions(changeOptions: KotlinIdeDescriptorOptions.() -> Unit): KotlinIdeDescriptorRenderer {
            val options = KotlinIdeDescriptorOptions()
            options.changeOptions()
            options.lock()
            return KotlinIdeDescriptorRenderer(options)
        }

        private val STANDARD_JAVA_ALIASES = setOf(
            "kotlin.collections.RandomAccess",
            "kotlin.collections.ArrayList",
            "kotlin.collections.LinkedHashMap",
            "kotlin.collections.HashMap",
            "kotlin.collections.LinkedHashSet",
            "kotlin.collections.HashSet"
        )
    }

    private val functionTypeAnnotationsRenderer: KotlinIdeDescriptorRenderer by lazy {
        withIdeOptions {
            excludedTypeAnnotationClasses += listOf(StandardNames.FqNames.extensionFunctionType)
        }
    }

    private fun StringBuilder.appendHighlighted(
        value: String,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<Attributes>.() -> Attributes
    ) {
        return with(overriddenHighlightingManager!!) { this@appendHighlighted.appendHighlighted(value, attributesBuilder()) }
    }

    private fun highlight(
        value: String,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<Attributes>.() -> Attributes
    ): String {
        return with(overriddenHighlightingManager!!) { buildString { appendHighlighted(value, attributesBuilder()) } }
    }

    private fun highlightByLexer(value: String): String {
        return with(overriddenHighlightingManager!!) { buildString { appendCodeSnippetHighlightedByLexer(value) } }
    }

    /* FORMATTING */
    protected fun renderKeyword(keyword: String): String {
        val highlighted = highlight(keyword) { asKeyword }
        return when (textFormat) {
            RenderingFormat.PLAIN -> highlighted
            RenderingFormat.HTML -> if (options.bold && !boldOnlyForNamesInHtml) "<b>$highlighted</b>" else highlighted
        }
    }

    protected fun renderError(message: String): String {
        val highlighted = highlight(message) { asError }
        return when (textFormat) {
            RenderingFormat.PLAIN -> highlighted
            RenderingFormat.HTML -> if (options.bold) "<b>$highlighted</b>" else highlighted
        }
    }

    protected fun escape(string: String) = textFormat.escape(string)

    protected fun lt() = highlight(escape("<")) { asOperationSign }
    protected fun gt() = highlight(escape(">")) { asOperationSign }

    protected fun arrow(): String {
        return highlight(escape("->")) { asArrow }
    }

    override fun renderMessage(message: String): String {
        val highlighted = highlight(message) { asInfo }
        return when (textFormat) {
            RenderingFormat.PLAIN -> highlighted
            RenderingFormat.HTML -> "<i>$highlighted</i>"
        }
    }

    /* NAMES RENDERING */
    override fun renderName(name: Name, rootRenderedElement: Boolean): String {
        val escaped = escape(name.render())
        return if (options.bold && boldOnlyForNamesInHtml && textFormat == RenderingFormat.HTML && rootRenderedElement) {
            "<b>$escaped</b>"
        } else
            escaped
    }

    protected fun StringBuilder.appendName(descriptor: DeclarationDescriptor, rootRenderedElement: Boolean) {
        append(renderName(descriptor.name, rootRenderedElement))
    }

    private fun StringBuilder.appendName(
        descriptor: DeclarationDescriptor,
        rootRenderedElement: Boolean,
        attributesBuilder: KotlinIdeDescriptorRendererHighlightingManager<Attributes>.() -> Attributes
    ) {
        return with(options.highlightingManager) {
            this@appendName.appendHighlighted(renderName(descriptor.name, rootRenderedElement), attributesBuilder())
        }
    }

    private fun StringBuilder.appendCompanionObjectName(descriptor: DeclarationDescriptor) {
        if (renderCompanionObjectName) {
            if (startFromName) {
                appendHighlighted("companion object") { asKeyword }
            }
            appendSpaceIfNeeded()
            val containingDeclaration = descriptor.containingDeclaration
            if (containingDeclaration != null) {
                appendHighlighted("of ") { asInfo }
                appendHighlighted(renderName(containingDeclaration.name, false)) { asObjectName }
            }
        }
        if (verbose || descriptor.name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
            if (!startFromName) appendSpaceIfNeeded()
            appendHighlighted(renderName(descriptor.name, true)) { asObjectName }
        }
    }

    override fun renderFqName(fqName: FqNameUnsafe) = renderFqName(fqName.pathSegments())

    private fun renderFqName(pathSegments: List<Name>): String {
        val rendered = buildString {
            for (element in pathSegments) {
                if (isNotEmpty()) {
                    appendHighlighted(".") { asDot }
                }
                appendHighlighted(element.render()) { asClassName }
            }
        }
        return escape(rendered)
    }

    override fun renderClassifierName(klass: ClassifierDescriptor): String = if (ErrorUtils.isError(klass)) {
        klass.typeConstructor.toString()
    } else
        classifierNamePolicy.renderClassifier(klass, this)

    /* TYPES RENDERING */
    override fun renderType(type: KotlinType): String = buildString {
        appendNormalizedType(typeNormalizer(type))
    }

    private fun StringBuilder.appendNormalizedType(type: KotlinType) {
        val abbreviated = type.unwrap() as? AbbreviatedType
        if (abbreviated != null) {
            if (renderTypeExpansions) {
                appendNormalizedTypeAsIs(abbreviated.expandedType)
            } else {
                // TODO nullability is lost for abbreviated type?
                appendNormalizedTypeAsIs(abbreviated.abbreviation)
                if (renderUnabbreviatedType) {
                    appendAbbreviatedTypeExpansion(abbreviated)
                }
            }
            return
        }

        appendNormalizedTypeAsIs(type)
    }

    private fun StringBuilder.appendAbbreviatedTypeExpansion(abbreviated: AbbreviatedType) {
        if (options.doNotExpandStandardJavaTypeAliases && abbreviated.fqName?.asString() in STANDARD_JAVA_ALIASES) {
            return
        }
        if (textFormat == RenderingFormat.HTML) {
            append("<i>")
        }
        val expandedType = withNoHighlighting { buildString { appendNormalizedTypeAsIs(abbreviated.expandedType) } }
        appendHighlighted(" /* = $expandedType */") { asInfo }
        if (textFormat == RenderingFormat.HTML) {
            append("</i></font>")
        }
    }

    private fun StringBuilder.appendNormalizedTypeAsIs(type: KotlinType) {
        if (type is WrappedType && debugMode && !type.isComputed()) {
            appendHighlighted("<Not computed yet>") { asInfo }
            return
        }
        when (val unwrappedType = type.unwrap()) {
            is FlexibleType -> append(unwrappedType.render(this@KotlinIdeDescriptorRenderer, this@KotlinIdeDescriptorRenderer))
            is SimpleType -> appendSimpleType(unwrappedType)
        }
    }

    private fun StringBuilder.appendSimpleType(type: SimpleType) {
        if (type == CANT_INFER_FUNCTION_PARAM_TYPE || TypeUtils.isDontCarePlaceholder(type)) {
            appendHighlighted("???") { asError }
            return
        }
        if (ErrorUtils.isUninferredParameter(type)) {
            if (uninferredTypeParameterAsName) {
                append(renderError((type.constructor as UninferredParameterTypeConstructor).typeParameterDescriptor.name.toString()))
            } else {
                appendHighlighted("???") { asError }
            }
            return
        }

        if (type.isError) {
            appendDefaultType(type)
            return
        }
        if (shouldRenderAsPrettyFunctionType(type)) {
            appendFunctionType(type)
        } else {
            appendDefaultType(type)
        }
    }

    protected fun shouldRenderAsPrettyFunctionType(type: KotlinType): Boolean {
        return type.isBuiltinFunctionalType && type.arguments.none { it.isStarProjection }
    }

    override fun renderFlexibleType(lowerRendered: String, upperRendered: String, builtIns: KotlinBuiltIns): String {
        val lowerType = escape(StringUtil.removeHtmlTags(lowerRendered))
        val upperType = escape(StringUtil.removeHtmlTags(upperRendered))

        if (differsOnlyInNullability(lowerType, upperType)) {
            if (upperType.startsWith("(")) {
                // the case of complex type, e.g. (() -> Unit)?
                return buildString {
                    appendHighlighted("(") { asParentheses }
                    append(lowerRendered)
                    appendHighlighted(")") { asParentheses }
                    appendHighlighted("!") { asOperationSign }
                }
            }
            return buildString {
                append(lowerRendered)
                appendHighlighted("!") { asOperationSign }
            }
        }

        val kotlinCollectionsPrefix = classifierNamePolicy.renderClassifier(builtIns.collection, this)
            .let { escape(StringUtil.removeHtmlTags(it)) }
            .substringBefore("Collection")
        val mutablePrefix = "Mutable"
        // java.util.List<Foo> -> (Mutable)List<Foo!>!
        val simpleCollection = replacePrefixes(
            lowerType,
            kotlinCollectionsPrefix + mutablePrefix,
            upperType,
            kotlinCollectionsPrefix,
            "$kotlinCollectionsPrefix($mutablePrefix)"
        )
        if (simpleCollection != null) return simpleCollection
        // java.util.Map.Entry<Foo, Bar> -> (Mutable)Map.(Mutable)Entry<Foo!, Bar!>!
        val mutableEntry = replacePrefixes(
            lowerType,
            kotlinCollectionsPrefix + "MutableMap.MutableEntry",
            upperType,
            kotlinCollectionsPrefix + "Map.Entry",
            "$kotlinCollectionsPrefix(Mutable)${highlight("Map") { asClassName }}.(Mutable)${highlight("Entry") { asClassName }}"
        )
        if (mutableEntry != null) return mutableEntry

        val kotlinPrefix = classifierNamePolicy.renderClassifier(builtIns.array, this)
            .let { escape(StringUtil.removeHtmlTags(it)) }
            .substringBefore("Array")
        // Foo[] -> Array<(out) Foo!>!
        val array = replacePrefixes(
            lowerType,
            kotlinPrefix + escape("Array<"),
            upperType,
            kotlinPrefix + escape("Array<out "),
            kotlinPrefix + highlight("Array") { asClassName } + escape("<(out) ")
        )
        if (array != null) return array

        return "($lowerRendered..$upperRendered)"
    }

    override fun renderTypeArguments(typeArguments: List<TypeProjection>): String = if (typeArguments.isEmpty()) ""
    else buildString {
        append(lt())
        appendTypeProjections(typeArguments)
        append(gt())
    }

    private fun StringBuilder.appendDefaultType(type: KotlinType) {
        appendAnnotations(type)

        if (type.isError) {
            if (type is UnresolvedType && presentableUnresolvedTypes) {
                appendHighlighted(type.presentableName) { asError }
            } else {
                if (type is ErrorType && !informativeErrorType) {
                    appendHighlighted(type.presentableName) { asError }
                } else {
                    appendHighlighted(type.constructor.toString()) { asError } // Debug name of an error type is more informative
                }
            }
            appendHighlighted(renderTypeArguments(type.arguments)) { asError }
        } else {
            appendTypeConstructorAndArguments(type)
        }

        if (type.isMarkedNullable) {
            appendHighlighted("?") { asNullityMarker }
        }

        if (type.isDefinitelyNotNullType) {
            appendHighlighted("!!") { asNonNullAssertion }
        }
    }

    private fun StringBuilder.appendTypeConstructorAndArguments(
        type: KotlinType,
        typeConstructor: TypeConstructor = type.constructor
    ) {
        val possiblyInnerType = type.buildPossiblyInnerType()
        if (possiblyInnerType == null) {
            append(renderTypeConstructor(typeConstructor))
            append(renderTypeArguments(type.arguments))
            return
        }

        appendPossiblyInnerType(possiblyInnerType)
    }

    private fun StringBuilder.appendPossiblyInnerType(possiblyInnerType: PossiblyInnerType) {
        possiblyInnerType.outerType?.let {
            appendPossiblyInnerType(it)
            appendHighlighted(".") { asDot }
            appendHighlighted(renderName(possiblyInnerType.classifierDescriptor.name, false)) { asClassName }
        } ?: append(renderTypeConstructor(possiblyInnerType.classifierDescriptor.typeConstructor))

        append(renderTypeArguments(possiblyInnerType.arguments))
    }

    override fun renderTypeConstructor(typeConstructor: TypeConstructor): String = when (val cd = typeConstructor.declarationDescriptor) {
        is TypeParameterDescriptor -> highlight(renderClassifierName(cd)) { asTypeParameterName }
        is ClassDescriptor -> highlight(renderClassifierName(cd)) { asClassName }
        is TypeAliasDescriptor -> highlight(renderClassifierName(cd)) { asTypeAlias }
        null -> highlight(escape(typeConstructor.toString())) { asClassName }
        else -> error("Unexpected classifier: " + cd::class.java)
    }

    override fun renderTypeProjection(typeProjection: TypeProjection) = buildString {
        appendTypeProjections(listOf(typeProjection))
    }

    private fun StringBuilder.appendTypeProjections(typeProjections: List<TypeProjection>) {
        typeProjections.joinTo(this, highlight(", ") { asComma }) {
            if (it.isStarProjection) {
                highlight("*") { asOperationSign }
            } else {
                val type = renderType(it.type)
                if (it.projectionKind == Variance.INVARIANT) type
                else "${highlight(it.projectionKind.toString()) { asKeyword }} $type"
            }
        }
    }

    private fun StringBuilder.appendFunctionType(type: KotlinType) {
        val lengthBefore = length
        // we need special renderer to skip @ExtensionFunctionType
        with(functionTypeAnnotationsRenderer) {
            appendAnnotations(type)
        }
        val hasAnnotations = length != lengthBefore

        val isSuspend = type.isSuspendFunctionType
        val isNullable = type.isMarkedNullable
        val receiverType = type.getReceiverTypeFromFunctionType()

        val needParenthesis = isNullable || (hasAnnotations && receiverType != null)
        if (needParenthesis) {
            if (isSuspend) {
                insert(lengthBefore, highlight("(") { asParentheses })
            } else {
                if (hasAnnotations) {
                    assert(last().isWhitespace())
                    if (get(lastIndex - 1) != ')') {
                        // last annotation rendered without parenthesis - need to add them otherwise parsing will be incorrect
                        insert(lastIndex, highlight("()") { asParentheses })
                    }
                }

                appendHighlighted("(") { asParentheses }
            }
        }

        appendModifier(isSuspend, "suspend")

        if (receiverType != null) {
            val surroundReceiver = shouldRenderAsPrettyFunctionType(receiverType) && !receiverType.isMarkedNullable ||
                    receiverType.hasModifiersOrAnnotations()
            if (surroundReceiver) {
                appendHighlighted("(") { asParentheses }
            }
            appendNormalizedType(receiverType)
            if (surroundReceiver) {
                appendHighlighted(")") { asParentheses }
            }
            appendHighlighted(".") { asDot }
        }

        appendHighlighted("(") { asParentheses }

        val parameterTypes = type.getValueParameterTypesFromFunctionType()
        for ((index, typeProjection) in parameterTypes.withIndex()) {
            if (index > 0) appendHighlighted(", ") { asComma }

            val name = if (parameterNamesInFunctionalTypes) typeProjection.type.extractParameterNameFromFunctionTypeArgument() else null
            if (name != null) {
                appendHighlighted(renderName(name, false)) { asParameter }
                appendHighlighted(": ") { asColon }
            }

            append(renderTypeProjection(typeProjection))
        }

        appendHighlighted(") ") { asParentheses }
        append(arrow())
        append(" ")
        appendNormalizedType(type.getReturnTypeFromFunctionType())

        if (needParenthesis) appendHighlighted(")") { asParentheses }

        if (isNullable) appendHighlighted("?") { asNullityMarker }
    }

    protected fun KotlinType.hasModifiersOrAnnotations() =
        isSuspendFunctionType || !annotations.isEmpty()

    /* METHODS FOR ALL KINDS OF DESCRIPTORS */
    private fun StringBuilder.appendDefinedIn(descriptor: DeclarationDescriptor) {
        if (descriptor is PackageFragmentDescriptor || descriptor is PackageViewDescriptor) {
            return
        }
        if (descriptor is ModuleDescriptor) {
            append(renderMessage(" is a module"))
            return
        }

        val containingDeclaration = descriptor.containingDeclaration
        if (containingDeclaration != null && containingDeclaration !is ModuleDescriptor) {
            append(renderMessage(" defined in "))
            val fqName = DescriptorUtils.getFqName(containingDeclaration)
            append(if (fqName.isRoot) "root package" else renderFqName(fqName))

            if (withSourceFileForTopLevel &&
                containingDeclaration is PackageFragmentDescriptor &&
                descriptor is DeclarationDescriptorWithSource
            ) {
                descriptor.source.containingFile.name?.let { sourceFileName ->
                    append(renderMessage(" in file "))
                    append(sourceFileName)
                }
            }
        }
    }

    private fun StringBuilder.appendAnnotations(
        annotated: Annotated,
        placeEachAnnotationOnNewLine: Boolean = false,
        target: AnnotationUseSiteTarget? = null
    ) {
        if (DescriptorRendererModifier.ANNOTATIONS !in modifiers) return

        val excluded = if (annotated is KotlinType) excludedTypeAnnotationClasses else excludedAnnotationClasses

        val annotationFilter = annotationFilter
        for (annotation in annotated.annotations) {
            if (annotation.fqName !in excluded
                && !annotation.isParameterName()
                && (annotationFilter == null || annotationFilter(annotation))
            ) {
                append(renderAnnotation(annotation, target))
                if (placeEachAnnotationOnNewLine) {
                    appendLine()
                } else {
                    append(" ")
                }
            }
        }
    }

    protected fun AnnotationDescriptor.isParameterName(): Boolean {
        return fqName == StandardNames.FqNames.parameterName
    }

    override fun renderAnnotation(annotation: AnnotationDescriptor, target: AnnotationUseSiteTarget?): String {
        return buildString {
            appendHighlighted("@") { asAnnotationName }
            if (target != null) {
                appendHighlighted(target.renderName) { asKeyword }
                appendHighlighted(":") { asAnnotationName }
            }
            val annotationType = annotation.type
            val renderedAnnotationName = withNoHighlighting { renderType(annotationType) }
            appendHighlighted(renderedAnnotationName) { asAnnotationName }

            if (includeAnnotationArguments) {
                val arguments = renderAndSortAnnotationArguments(annotation)
                if (includeEmptyAnnotationArguments || arguments.isNotEmpty()) {
                    arguments.joinTo(
                        this, highlight(", ") { asComma }, highlight("(") { asParentheses }, highlight(")") { asParentheses })
                }
            }

            if (verbose && (annotationType.isError || annotationType.constructor.declarationDescriptor is NotFoundClasses.MockClassDescriptor)) {
                appendHighlighted(" /* annotation class not found */") { asError }
            }
        }
    }

    private fun renderAndSortAnnotationArguments(descriptor: AnnotationDescriptor): List<String> {
        val allValueArguments = descriptor.allValueArguments
        val classDescriptor = if (renderDefaultAnnotationArguments) descriptor.annotationClass else null
        val parameterDescriptorsWithDefaultValue = classDescriptor?.unsubstitutedPrimaryConstructor?.valueParameters
            ?.filter { it.declaresDefaultValue() }
            ?.map { it.name }
            .orEmpty()
        val defaultList = parameterDescriptorsWithDefaultValue
            .filter { it !in allValueArguments }
            .map {
                buildString {
                    appendHighlighted(it.asString()) { asAnnotationAttributeName }
                    appendHighlighted(" = ") { asOperationSign }
                    appendHighlighted("...") { asInfo }
                }
            }
        val argumentList = allValueArguments.entries
            .map { (name, value) ->
                buildString {
                    appendHighlighted(name.asString()) { asAnnotationAttributeName }
                    appendHighlighted(" = ") { asOperationSign }
                    if (name !in parameterDescriptorsWithDefaultValue) {
                        append(renderConstant(value))
                    } else {
                        appendHighlighted("...") { asInfo }
                    }
                }
            }
        return (defaultList + argumentList).sorted()
    }

    private fun renderConstant(value: ConstantValue<*>): String {
        return when (value) {
            is ArrayValue -> {
                buildString {
                    appendHighlighted("{") { asBraces }
                    value.value.joinTo(this, highlight(", ") { asComma }) { renderConstant(it) }
                    appendHighlighted("}") { asBraces }
                }
            }
            is AnnotationValue -> renderAnnotation(value.value).removePrefix("@")
            is KClassValue -> when (val classValue = value.value) {
                is KClassValue.Value.LocalClass -> buildString {
                    appendHighlighted(classValue.type.toString()) { asClassName }
                    appendHighlighted("::") { asDoubleColon }
                    appendHighlighted("class") { asKeyword }
                }
                is KClassValue.Value.NormalClass -> {
                    var type = classValue.classId.asSingleFqName().asString()
                    repeat(classValue.arrayDimensions) {
                        type = buildString {
                            appendHighlighted("kotlin") { asClassName }
                            appendHighlighted(".") { asDot }
                            appendHighlighted("Array") { asClassName }
                            appendHighlighted("<") { asOperationSign }
                            append(type)
                            appendHighlighted(">") { asOperationSign }
                        }
                    }
                    buildString {
                        append(type)
                        appendHighlighted("::") { asDoubleColon }
                        appendHighlighted("class") { asKeyword }
                    }
                }
            }
            else -> highlightByLexer(value.toString())
        }
    }

    private fun StringBuilder.appendVisibility(visibility: DescriptorVisibility): Boolean {
        @Suppress("NAME_SHADOWING")
        var visibility = visibility
        if (DescriptorRendererModifier.VISIBILITY !in modifiers) return false
        if (normalizedVisibilities) {
            visibility = visibility.normalize()
        }
        if (!renderDefaultVisibility && visibility == DescriptorVisibilities.DEFAULT_VISIBILITY) return false
        append(renderKeyword(visibility.internalDisplayName))
        append(" ")
        return true
    }

    private fun StringBuilder.appendModality(modality: Modality, defaultModality: Modality) {
        if (!renderDefaultModality && modality == defaultModality) return
        appendModifier(DescriptorRendererModifier.MODALITY in modifiers, modality.name.toLowerCaseAsciiOnly())
    }

    private fun MemberDescriptor.implicitModalityWithoutExtensions(): Modality {
        if (this is ClassDescriptor) {
            return if (kind == ClassKind.INTERFACE) Modality.ABSTRACT else Modality.FINAL
        }
        val containingClassDescriptor = containingDeclaration as? ClassDescriptor ?: return Modality.FINAL
        if (this !is CallableMemberDescriptor) return Modality.FINAL
        if (this.overriddenDescriptors.isNotEmpty()) {
            if (containingClassDescriptor.modality != Modality.FINAL) return Modality.OPEN
        }
        return if (containingClassDescriptor.kind == ClassKind.INTERFACE && this.visibility != DescriptorVisibilities.PRIVATE) {
            if (this.modality == Modality.ABSTRACT) Modality.ABSTRACT else Modality.OPEN
        } else
            Modality.FINAL
    }

    private fun StringBuilder.appendModalityForCallable(callable: CallableMemberDescriptor) {
        if (!DescriptorUtils.isTopLevelDeclaration(callable) || callable.modality != Modality.FINAL) {
            if (overrideRenderingPolicy == OverrideRenderingPolicy.RENDER_OVERRIDE && callable.modality == Modality.OPEN &&
                overridesSomething(callable)
            ) {
                return
            }
            appendModality(callable.modality, callable.implicitModalityWithoutExtensions())
        }
    }

    private fun StringBuilder.appendOverride(callableMember: CallableMemberDescriptor) {
        if (DescriptorRendererModifier.OVERRIDE !in modifiers) return
        if (overridesSomething(callableMember)) {
            if (overrideRenderingPolicy != OverrideRenderingPolicy.RENDER_OPEN) {
                appendModifier(true, "override")
                if (verbose) {
                    appendHighlighted("/*${callableMember.overriddenDescriptors.size}*/ ") { asInfo }
                }
            }
        }
    }

    private fun StringBuilder.appendMemberKind(callableMember: CallableMemberDescriptor) {
        if (DescriptorRendererModifier.MEMBER_KIND !in modifiers) return
        if (verbose && callableMember.kind != CallableMemberDescriptor.Kind.DECLARATION) {
            appendHighlighted("/*${callableMember.kind.name.toLowerCaseAsciiOnly()}*/ ") { asInfo }
        }
    }

    private fun StringBuilder.appendModifier(value: Boolean, modifier: String) {
        if (value) {
            append(renderKeyword(modifier))
            append(" ")
        }
    }

    private fun StringBuilder.appendMemberModifiers(descriptor: MemberDescriptor) {
        appendModifier(descriptor.isExternal, "external")
        appendModifier(DescriptorRendererModifier.EXPECT in modifiers && descriptor.isExpect, "expect")
        appendModifier(DescriptorRendererModifier.ACTUAL in modifiers && descriptor.isActual, "actual")
    }

    private fun StringBuilder.appendAdditionalModifiers(functionDescriptor: FunctionDescriptor) {
        val isOperator =
            functionDescriptor.isOperator && (functionDescriptor.overriddenDescriptors.none { it.isOperator } || alwaysRenderModifiers)
        val isInfix =
            functionDescriptor.isInfix && (functionDescriptor.overriddenDescriptors.none { it.isInfix } || alwaysRenderModifiers)

        appendModifier(functionDescriptor.isTailrec, "tailrec")
        appendSuspendModifier(functionDescriptor)
        appendModifier(functionDescriptor.isInline, "inline")
        appendModifier(isInfix, "infix")
        appendModifier(isOperator, "operator")
    }

    private fun StringBuilder.appendSuspendModifier(functionDescriptor: FunctionDescriptor) {
        appendModifier(functionDescriptor.isSuspend, "suspend")
    }

    override fun render(declarationDescriptor: DeclarationDescriptor): String {
        return buildString {
            declarationDescriptor.accept(RenderDeclarationDescriptorVisitor(), this)

            if (withDefinedIn) {
                appendDefinedIn(declarationDescriptor)
            }
        }
    }


    /* TYPE PARAMETERS */
    private fun StringBuilder.appendTypeParameter(typeParameter: TypeParameterDescriptor, topLevel: Boolean) {
        if (topLevel) {
            append(lt())
        }

        if (verbose) {
            appendHighlighted("/*${typeParameter.index}*/ ") { asInfo }
        }

        appendModifier(typeParameter.isReified, "reified")
        val variance = typeParameter.variance.label
        appendModifier(variance.isNotEmpty(), variance)

        appendAnnotations(typeParameter)

        appendName(typeParameter, topLevel) { asTypeParameterName }
        val upperBoundsCount = typeParameter.upperBounds.size
        if ((upperBoundsCount > 1 && !topLevel) || upperBoundsCount == 1) {
            val upperBound = typeParameter.upperBounds.iterator().next()
            if (!KotlinBuiltIns.isDefaultBound(upperBound)) {
                appendHighlighted(" : ") { asColon }
                append(renderType(upperBound))
            }
        } else if (topLevel) {
            var first = true
            for (upperBound in typeParameter.upperBounds) {
                if (KotlinBuiltIns.isDefaultBound(upperBound)) {
                    continue
                }
                if (first) {
                    appendHighlighted(" : ") { asColon }
                } else {
                    appendHighlighted(" & ") { asOperationSign }
                }
                append(renderType(upperBound))
                first = false
            }
        } else {
            // rendered with "where"
        }

        if (topLevel) {
            append(gt())
        }
    }

    private fun StringBuilder.appendTypeParameters(typeParameters: List<TypeParameterDescriptor>, withSpace: Boolean) {
        if (withoutTypeParameters) return

        if (typeParameters.isNotEmpty()) {
            append(lt())
            appendTypeParameterList(typeParameters)
            append(gt())
            if (withSpace) {
                append(" ")
            }
        }
    }

    private fun StringBuilder.appendTypeParameterList(typeParameters: List<TypeParameterDescriptor>) {
        val iterator = typeParameters.iterator()
        while (iterator.hasNext()) {
            val typeParameterDescriptor = iterator.next()
            appendTypeParameter(typeParameterDescriptor, false)
            if (iterator.hasNext()) {
                appendHighlighted(", ") { asComma }
            }
        }
    }

    /* FUNCTIONS */
    private fun StringBuilder.appendFunction(function: FunctionDescriptor) {
        if (!startFromName) {
            if (!startFromDeclarationKeyword) {
                appendAnnotations(function, eachAnnotationOnNewLine)
                appendVisibility(function.visibility)
                appendModalityForCallable(function)

                if (includeAdditionalModifiers) {
                    appendMemberModifiers(function)
                }

                appendOverride(function)

                if (includeAdditionalModifiers) {
                    appendAdditionalModifiers(function)
                } else {
                    appendSuspendModifier(function)
                }

                appendMemberKind(function)

                if (verbose) {
                    if (function.isHiddenToOvercomeSignatureClash) {
                        appendHighlighted("/*isHiddenToOvercomeSignatureClash*/ ") { asInfo }
                    }

                    if (function.isHiddenForResolutionEverywhereBesideSupercalls) {
                        appendHighlighted("/*isHiddenForResolutionEverywhereBesideSupercalls*/ ") { asInfo }
                    }
                }
            }

            append(renderKeyword("fun"))
            append(" ")
            appendTypeParameters(function.typeParameters, true)
            appendReceiver(function)
        }

        appendName(function, true) { asFunDeclaration }

        appendValueParameters(function.valueParameters, function.hasSynthesizedParameterNames())

        appendReceiverAfterName(function)

        val returnType = function.returnType
        if (!withoutReturnType && (unitReturnType || (returnType == null || !KotlinBuiltIns.isUnit(returnType)))) {
            appendHighlighted(": ") { asColon }
            append(if (returnType == null) highlight("[NULL]") { asError } else renderType(returnType))
        }

        appendWhereSuffix(function.typeParameters)
    }

    private fun StringBuilder.appendReceiverAfterName(callableDescriptor: CallableDescriptor) {
        if (!receiverAfterName) return

        val receiver = callableDescriptor.extensionReceiverParameter
        if (receiver != null) {
            appendHighlighted(" on ") { asInfo }
            append(renderType(receiver.type))
        }
    }

    private fun StringBuilder.appendReceiver(callableDescriptor: CallableDescriptor) {
        val receiver = callableDescriptor.extensionReceiverParameter
        if (receiver != null) {
            appendAnnotations(receiver, target = AnnotationUseSiteTarget.RECEIVER)

            val type = receiver.type
            var result = renderType(type)
            if (shouldRenderAsPrettyFunctionType(type) && !TypeUtils.isNullableType(type)) {
                result = "${highlight("(") { asParentheses }}$result${highlight(")") { asParentheses }}"
            }
            append(result)
            appendHighlighted(".") { asDot }
        }
    }

    private fun StringBuilder.appendConstructor(constructor: ConstructorDescriptor) {
        appendAnnotations(constructor)
        val visibilityRendered = (options.renderDefaultVisibility || constructor.constructedClass.modality != Modality.SEALED)
                && appendVisibility(constructor.visibility)
        appendMemberKind(constructor)

        val constructorKeywordRendered = renderConstructorKeyword || !constructor.isPrimary || visibilityRendered
        if (constructorKeywordRendered) {
            append(renderKeyword("constructor"))
        }
        val classDescriptor = constructor.containingDeclaration
        if (secondaryConstructorsAsPrimary) {
            if (constructorKeywordRendered) {
                append(" ")
            }
            appendName(classDescriptor, true) { asClassName }
            appendTypeParameters(constructor.typeParameters, false)
        }

        appendValueParameters(constructor.valueParameters, constructor.hasSynthesizedParameterNames())

        if (renderConstructorDelegation && !constructor.isPrimary && classDescriptor is ClassDescriptor) {
            val primaryConstructor = classDescriptor.unsubstitutedPrimaryConstructor
            if (primaryConstructor != null) {
                val parametersWithoutDefault = primaryConstructor.valueParameters.filter {
                    !it.declaresDefaultValue() && it.varargElementType == null
                }
                if (parametersWithoutDefault.isNotEmpty()) {
                    appendHighlighted(" : ") { asColon }
                    append(renderKeyword("this"))
                    append(parametersWithoutDefault.joinToString(
                        prefix = highlight("(") { asParentheses },
                        separator = highlight(", ") { asComma },
                        postfix = highlight(")") { asParentheses }
                    ) { "" }
                    )
                }
            }
        }

        if (secondaryConstructorsAsPrimary) {
            appendWhereSuffix(constructor.typeParameters)
        }
    }

    private fun StringBuilder.appendWhereSuffix(typeParameters: List<TypeParameterDescriptor>) {
        if (withoutTypeParameters) return

        val upperBoundStrings = ArrayList<String>(0)

        for (typeParameter in typeParameters) {
            typeParameter.upperBounds
                .drop(1) // first parameter is rendered by renderTypeParameter
                .mapTo(upperBoundStrings) {
                    buildString {
                        appendHighlighted(renderName(typeParameter.name, false)) { asTypeParameterName }
                        appendHighlighted(" : ") { asColon }
                        append(renderType(it))
                    }
                }
        }

        if (upperBoundStrings.isNotEmpty()) {
            append(" ")
            append(renderKeyword("where"))
            append(" ")
            upperBoundStrings.joinTo(this, highlight(", ") { asComma })
        }
    }

    override fun renderValueParameters(parameters: Collection<ValueParameterDescriptor>, synthesizedParameterNames: Boolean) = buildString {
        appendValueParameters(parameters, synthesizedParameterNames)
    }

    private fun StringBuilder.appendValueParameters(
        parameters: Collection<ValueParameterDescriptor>,
        synthesizedParameterNames: Boolean
    ) {
        val includeNames = shouldRenderParameterNames(synthesizedParameterNames)
        val parameterCount = parameters.size
        valueParametersHandler.appendBeforeValueParameters(parameterCount, this)
        for ((index, parameter) in parameters.withIndex()) {
            valueParametersHandler.appendBeforeValueParameter(parameter, index, parameterCount, this)
            appendValueParameter(parameter, includeNames, false)
            valueParametersHandler.appendAfterValueParameter(parameter, index, parameterCount, this)
        }
        valueParametersHandler.appendAfterValueParameters(parameterCount, this)
    }

    private fun shouldRenderParameterNames(synthesizedParameterNames: Boolean): Boolean = when (parameterNameRenderingPolicy) {
        ParameterNameRenderingPolicy.ALL -> true
        ParameterNameRenderingPolicy.ONLY_NON_SYNTHESIZED -> !synthesizedParameterNames
        ParameterNameRenderingPolicy.NONE -> false
    }

    /* VARIABLES */
    private fun StringBuilder.appendValueParameter(
        valueParameter: ValueParameterDescriptor,
        includeName: Boolean,
        topLevel: Boolean
    ) {
        if (topLevel) {
            append(renderKeyword("value-parameter"))
            append(" ")
        }

        if (verbose) {
            appendHighlighted("/*${valueParameter.index}*/ ") { asInfo }
        }

        appendAnnotations(valueParameter)
        appendModifier(valueParameter.isCrossinline, "crossinline")
        appendModifier(valueParameter.isNoinline, "noinline")

        val isPrimaryConstructor = renderPrimaryConstructorParametersAsProperties &&
                (valueParameter.containingDeclaration as? ClassConstructorDescriptor)?.isPrimary == true
        if (isPrimaryConstructor) {
            appendModifier(actualPropertiesInPrimaryConstructor, "actual")
        }

        appendVariable(valueParameter, includeName, topLevel, isPrimaryConstructor)

        val withDefaultValue =
            defaultParameterValueRenderer != null &&
                    (if (debugMode) valueParameter.declaresDefaultValue() else valueParameter.declaresOrInheritsDefaultValue())
        if (withDefaultValue) {
            appendHighlighted(" = ") { asOperationSign }
            append(highlightByLexer(defaultParameterValueRenderer!!(valueParameter)))
        }
    }

    private fun StringBuilder.appendValVarPrefix(variable: VariableDescriptor, isInPrimaryConstructor: Boolean = false) {
        if (isInPrimaryConstructor || variable !is ValueParameterDescriptor) {
            if (variable.isVar) {
                appendHighlighted("var") { asVar }
            } else {
                appendHighlighted("val") { asVal }
            }
            append(" ")
        }
    }

    private fun StringBuilder.appendVariable(
        variable: VariableDescriptor,
        includeName: Boolean,
        topLevel: Boolean,
        isInPrimaryConstructor: Boolean = false
    ) {
        val realType = variable.type

        val varargElementType = (variable as? ValueParameterDescriptor)?.varargElementType
        val typeToRender = varargElementType ?: realType
        appendModifier(varargElementType != null, "vararg")

        if (isInPrimaryConstructor || topLevel && !startFromName) {
            appendValVarPrefix(variable, isInPrimaryConstructor)
        }

        if (includeName) {
            appendName(variable, topLevel) { asLocalVarOrVal }
            appendHighlighted(": ") { asColon }
        }

        append(renderType(typeToRender))

        appendInitializer(variable)

        if (verbose && varargElementType != null) {
            val expandedType = withNoHighlighting { renderType(realType) }
            appendHighlighted(" /*${expandedType}*/") { asInfo }
        }
    }

    private fun StringBuilder.appendProperty(property: PropertyDescriptor) {
        if (!startFromName) {
            if (!startFromDeclarationKeyword) {
                appendPropertyAnnotations(property)
                appendVisibility(property.visibility)
                appendModifier(DescriptorRendererModifier.CONST in modifiers && property.isConst, "const")
                appendMemberModifiers(property)
                appendModalityForCallable(property)
                appendOverride(property)
                appendModifier(DescriptorRendererModifier.LATEINIT in modifiers && property.isLateInit, "lateinit")
                appendMemberKind(property)
            }
            appendValVarPrefix(property)
            appendTypeParameters(property.typeParameters, true)
            appendReceiver(property)
        }

        appendName(property, true) { asInstanceProperty }
        appendHighlighted(": ") { asColon }
        append(renderType(property.type))

        appendReceiverAfterName(property)

        appendInitializer(property)

        appendWhereSuffix(property.typeParameters)
    }

    private fun StringBuilder.appendPropertyAnnotations(property: PropertyDescriptor) {
        if (DescriptorRendererModifier.ANNOTATIONS !in modifiers) return

        appendAnnotations(property, eachAnnotationOnNewLine)

        property.backingField?.let { appendAnnotations(it, target = AnnotationUseSiteTarget.FIELD) }
        property.delegateField?.let { appendAnnotations(it, target = AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD) }

        if (propertyAccessorRenderingPolicy == PropertyAccessorRenderingPolicy.NONE) {
            property.getter?.let {
                appendAnnotations(it, target = AnnotationUseSiteTarget.PROPERTY_GETTER)
            }
            property.setter?.let { setter ->
                setter.let {
                    appendAnnotations(it, target = AnnotationUseSiteTarget.PROPERTY_SETTER)
                }
                setter.valueParameters.single().let {
                    appendAnnotations(it, target = AnnotationUseSiteTarget.SETTER_PARAMETER)
                }
            }
        }
    }

    private fun StringBuilder.appendInitializer(variable: VariableDescriptor) {
        if (includePropertyConstant) {
            variable.compileTimeInitializer?.let { constant ->
                appendHighlighted(" = ") { asOperationSign }
                append(escape(renderConstant(constant)))
            }
        }
    }

    private fun StringBuilder.appendTypeAlias(typeAlias: TypeAliasDescriptor) {
        appendAnnotations(typeAlias, eachAnnotationOnNewLine)
        appendVisibility(typeAlias.visibility)
        appendMemberModifiers(typeAlias)
        append(renderKeyword("typealias"))
        append(" ")
        appendName(typeAlias, true) { asTypeAlias }

        appendTypeParameters(typeAlias.declaredTypeParameters, false)
        appendCapturedTypeParametersIfRequired(typeAlias)

        appendHighlighted(" = ") { asOperationSign }
        append(renderType(typeAlias.underlyingType))
    }

    private fun StringBuilder.appendCapturedTypeParametersIfRequired(classifier: ClassifierDescriptorWithTypeParameters) {
        val typeParameters = classifier.declaredTypeParameters
        val typeConstructorParameters = classifier.typeConstructor.parameters

        if (verbose && classifier.isInner && typeConstructorParameters.size > typeParameters.size) {
            val capturedTypeParametersInfo = buildString {
                append(" /*captured type parameters: ")
                withNoHighlighting {
                    appendTypeParameterList(typeConstructorParameters.subList(typeParameters.size, typeConstructorParameters.size))
                }
                append("*/")
            }
            appendHighlighted(capturedTypeParametersInfo) { asInfo }
        }
    }

    /* CLASSES */
    private fun StringBuilder.appendClass(klass: ClassDescriptor) {
        val isEnumEntry = klass.kind == ClassKind.ENUM_ENTRY

        if (!startFromName) {
            appendAnnotations(klass, eachAnnotationOnNewLine)
            if (!isEnumEntry) {
                appendVisibility(klass.visibility)
            }
            if (!(klass.kind == ClassKind.INTERFACE && klass.modality == Modality.ABSTRACT ||
                        klass.kind.isSingleton && klass.modality == Modality.FINAL)
            ) {
                appendModality(klass.modality, klass.implicitModalityWithoutExtensions())
            }
            appendMemberModifiers(klass)
            appendModifier(DescriptorRendererModifier.INNER in modifiers && klass.isInner, "inner")
            appendModifier(DescriptorRendererModifier.DATA in modifiers && klass.isData, "data")
            appendModifier(DescriptorRendererModifier.INLINE in modifiers && klass.isInline, "inline")
            appendModifier(DescriptorRendererModifier.VALUE in modifiers && klass.isValue, "value")
            appendModifier(DescriptorRendererModifier.FUN in modifiers && klass.isFun, "fun")
            appendClassKindPrefix(klass)
        }

        if (!isCompanionObject(klass)) {
            if (!startFromName) appendSpaceIfNeeded()
            appendName(klass, true) { asClassName }
        } else {
            appendCompanionObjectName(klass)
        }

        if (isEnumEntry) return

        val typeParameters = klass.declaredTypeParameters
        appendTypeParameters(typeParameters, false)
        appendCapturedTypeParametersIfRequired(klass)

        val primaryConstructor = klass.unsubstitutedPrimaryConstructor
        if (primaryConstructor != null &&
            (klass.isData && options.dataClassWithPrimaryConstructor || !klass.kind.isSingleton && classWithPrimaryConstructor)
        ) {
            if (!klass.isData || !primaryConstructor.annotations.isEmpty()) {
                append(" ")
                appendAnnotations(primaryConstructor)
                appendVisibility(primaryConstructor.visibility)
                append(renderKeyword("constructor"))
            }
            appendValueParameters(primaryConstructor.valueParameters, primaryConstructor.hasSynthesizedParameterNames())
            appendSuperTypes(klass, indent = "  ")
        } else {
            appendSuperTypes(klass, prefix = "\n    ")
        }

        appendWhereSuffix(typeParameters)
    }

    private fun StringBuilder.appendSuperTypes(klass: ClassDescriptor, prefix: String = " ", indent: String = "    ") {
        if (withoutSuperTypes) return

        if (KotlinBuiltIns.isNothing(klass.defaultType)) return

        val supertypes = klass.typeConstructor.supertypes.toMutableList()
        if (klass.kind == ClassKind.ENUM_CLASS) {
            supertypes.removeIf { KotlinBuiltIns.isEnum(it) }
        }
        if (supertypes.isEmpty() || supertypes.size == 1 && KotlinBuiltIns.isAnyOrNullableAny(supertypes.iterator().next())) return

        append(prefix)
        appendHighlighted(": ") { asColon }
        val separator = when {
            supertypes.size <= 3 -> ", "
            else -> ",\n$indent  "
        }
        supertypes.joinTo(this, highlight(separator) { asComma }) { renderType(it) }
    }

    private fun StringBuilder.appendClassKindPrefix(klass: ClassDescriptor) {
        append(renderKeyword(getClassifierKindPrefix(klass)))
    }


    /* OTHER */
    private fun StringBuilder.appendPackageView(packageView: PackageViewDescriptor) {
        appendPackageHeader(packageView.fqName, "package")
        if (debugMode) {
            appendHighlighted(" in context of ") { asInfo }
            appendName(packageView.module, false) { asPackageName }
        }
    }

    private fun StringBuilder.appendPackageFragment(fragment: PackageFragmentDescriptor) {
        appendPackageHeader(fragment.fqName, "package-fragment")
        if (debugMode) {
            appendHighlighted(" in ") { asInfo }
            appendName(fragment.containingDeclaration, false) { asPackageName }
        }
    }

    private fun StringBuilder.appendPackageHeader(fqName: FqName, fragmentOrView: String) {
        append(renderKeyword(fragmentOrView))
        val fqNameString = renderFqName(fqName.toUnsafe())
        if (fqNameString.isNotEmpty()) {
            append(" ")
            append(fqNameString)
        }
    }

    private fun StringBuilder.appendAccessorModifiers(descriptor: PropertyAccessorDescriptor) {
        appendMemberModifiers(descriptor)
    }

    /* STUPID DISPATCH-ONLY VISITOR */
    private inner class RenderDeclarationDescriptorVisitor : DeclarationDescriptorVisitor<Unit, StringBuilder> {
        override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, builder: StringBuilder) {
            builder.appendValueParameter(descriptor, true, true)
        }

        override fun visitVariableDescriptor(descriptor: VariableDescriptor, builder: StringBuilder) {
            builder.appendVariable(descriptor, true, true)
        }

        override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, builder: StringBuilder) {
            builder.appendProperty(descriptor)
        }

        override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, builder: StringBuilder) {
            visitPropertyAccessorDescriptor(descriptor, builder, "getter")
        }

        override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, builder: StringBuilder) {
            visitPropertyAccessorDescriptor(descriptor, builder, "setter")
        }

        private fun visitPropertyAccessorDescriptor(descriptor: PropertyAccessorDescriptor, builder: StringBuilder, kind: String) {
            when (propertyAccessorRenderingPolicy) {
                PropertyAccessorRenderingPolicy.PRETTY -> {
                    builder.appendAccessorModifiers(descriptor)
                    builder.appendHighlighted("$kind for ") { asInfo }
                    builder.appendProperty(descriptor.correspondingProperty)
                }
                PropertyAccessorRenderingPolicy.DEBUG -> {
                    visitFunctionDescriptor(descriptor, builder)
                }
                PropertyAccessorRenderingPolicy.NONE -> {
                }
            }
        }

        override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, builder: StringBuilder) {
            builder.appendFunction(descriptor)
        }

        override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, builder: StringBuilder) {
            builder.append(descriptor.name) // renders <this>
        }

        override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, builder: StringBuilder) {
            builder.appendConstructor(constructorDescriptor)
        }

        override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, builder: StringBuilder) {
            builder.appendTypeParameter(descriptor, true)
        }

        override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, builder: StringBuilder) {
            builder.appendPackageFragment(descriptor)
        }

        override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, builder: StringBuilder) {
            builder.appendPackageView(descriptor)
        }

        override fun visitModuleDeclaration(descriptor: ModuleDescriptor, builder: StringBuilder) {
            builder.appendName(descriptor, true) { asPackageName }
        }

        override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, builder: StringBuilder) {
            visitClassDescriptor(scriptDescriptor, builder)
        }

        override fun visitClassDescriptor(descriptor: ClassDescriptor, builder: StringBuilder) {
            builder.appendClass(descriptor)
        }

        override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, builder: StringBuilder) {
            builder.appendTypeAlias(descriptor)
        }
    }

    private fun StringBuilder.appendSpaceIfNeeded() {
        if (isEmpty() || this[length - 1] != ' ') {
            append(' ')
        }
    }

    private fun replacePrefixes(
        lowerRendered: String,
        lowerPrefix: String,
        upperRendered: String,
        upperPrefix: String,
        foldedPrefix: String
    ): String? {
        if (lowerRendered.startsWith(lowerPrefix) && upperRendered.startsWith(upperPrefix)) {
            val lowerWithoutPrefix = lowerRendered.substring(lowerPrefix.length)
            val upperWithoutPrefix = upperRendered.substring(upperPrefix.length)
            val flexibleCollectionName = foldedPrefix + lowerWithoutPrefix
            return when {
                lowerWithoutPrefix == upperWithoutPrefix -> flexibleCollectionName
                differsOnlyInNullability(lowerWithoutPrefix, upperWithoutPrefix) -> "$flexibleCollectionName!"
                else -> null
            }
        }
        return null
    }

    private fun differsOnlyInNullability(lower: String, upper: String) =
        lower == upper.replace("?", "") || upper.endsWith("?") && ("$lower?") == upper || "($lower)?" == upper

    private fun overridesSomething(callable: CallableMemberDescriptor) = !callable.overriddenDescriptors.isEmpty()
}
