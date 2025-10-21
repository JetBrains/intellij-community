// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.*
import com.intellij.codeInspection.options.OptionController
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsMethodImpl
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.context.ContextDependency
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.util.DelegatingCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.SyntheticScopes
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.synthetic.canBePropertyAccessor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.util.shouldNotConvertToProperty
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

@Suppress("DEPRECATION")
class UsePropertyAccessSyntaxInspection : IntentionBasedInspection<KtExpression>(UsePropertyAccessSyntaxIntention::class),
                                          CleanupLocalInspectionTool {

    val fqNameList = NotPropertiesService.DEFAULT.map(::FqNameUnsafe).toMutableList()

    // Serialized setting
    @Suppress("MemberVisibilityCanBePrivate")
    var fqNameStrings = NotPropertiesService.DEFAULT.toMutableList()

    @Suppress("MemberVisibilityCanBePrivate")
    var reportNonTrivialAccessors = false

    override fun readSettings(node: Element) {
        super.readSettings(node)
        fqNameList.clear()
        fqNameStrings.mapTo(fqNameList, ::FqNameUnsafe)
    }

    override fun writeSettings(node: Element) {
        fqNameStrings.clear()
        fqNameList.mapTo(fqNameStrings) { it.asString() }
        super.writeSettings(node)
    }

    override fun getOptionsPane(): OptPane = pane(
        checkbox("reportNonTrivialAccessors", KotlinBundle.message("use.property.access.syntax.option.report.non.trivial.accessors")),
        stringList("fqNameStrings", KotlinBundle.message("excluded.methods")),
    )

    override fun getOptionController(): OptionController {
        return super.getOptionController()
            .onValueSet("fqNameStrings") { newList ->
                assert(newList === fqNameStrings)
                fqNameList.clear()
                fqNameStrings.mapTo(fqNameList, ::FqNameUnsafe)
            }
    }

    override fun inspectionTarget(element: KtExpression): PsiElement? =
        element.callOrReferenceOrNull(KtCallExpression::getCalleeExpression, KtCallableReferenceExpression::getCallableReference)

    override fun inspectionProblemText(element: KtExpression): String? =
        element.callOrReferenceOrNull(
            {
                when (it.valueArguments.size) {
                    0 -> KotlinBundle.message("use.of.getter.method.instead.of.property.access.syntax")
                    1 -> KotlinBundle.message("use.of.setter.method.instead.of.property.access.syntax")
                    else -> error("getter or setter arg length can't be !in 0..1")
                }
            },
            {
                KotlinBundle.message("use.of.getter.method.instead.of.property.access.syntax")
            }
        )

    override fun problemHighlightType(element: KtExpression): ProblemHighlightType {
        val defaultType = super.problemHighlightType(element)
        val function = (element as? KtCallExpression)?.functionDescriptor() ?: return defaultType
        if (reportNonTrivialAccessors || function.isTrivialAccessor(element.project)) return defaultType
        return INFORMATION
    }

    private fun KtCallExpression.functionDescriptor(): FunctionDescriptor? {
        val resolutionFacade = getResolutionFacade()
        val bindingContext = safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL_FOR_COMPLETION)
        val resolvedCall = getResolvedCall(bindingContext) ?: return null
        if (!resolvedCall.isReallySuccess()) return null
        return resolvedCall.resultingDescriptor as? FunctionDescriptor
    }

    private fun FunctionDescriptor.isTrivialAccessor(project: Project): Boolean {
        // Accessor is considered trivial if it has exactly one statement
        // Abstract methods are not trivial because they can be overridden by complex overrides
        fun PsiElement.isTrivialAccessor(): Boolean = when (this) {
            is KtNamedFunction -> bodyBlockExpression?.statements.orEmpty().size == 1
            is ClsMethodImpl -> {
                sourceMirrorMethod?.body?.statements?.let { it.size == 1 }
                    ?: true // skip compiled methods for which we can't get the source code
            }

            is PsiMethod -> body?.statements.orEmpty().size == 1
            else -> false
        }

        val accessors = DescriptorToSourceUtilsIde.getAllDeclarations(project, targetDescriptor = this)
        return accessors.all { it.isTrivialAccessor() }
    }
}

class NotPropertiesServiceImpl(private val project: Project) : NotPropertiesService {
    override fun getNotProperties(element: PsiElement): Set<FqNameUnsafe> {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val tool = profile.getUnwrappedTool(USE_PROPERTY_ACCESS_INSPECTION, element)
        return (tool?.fqNameList ?: NotPropertiesService.DEFAULT.map(::FqNameUnsafe)).toSet()
    }

    companion object {
        val USE_PROPERTY_ACCESS_INSPECTION: Key<UsePropertyAccessSyntaxInspection> = Key.create("UsePropertyAccessSyntax")
    }
}

/**
 * Affected tests:
 * [org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated.UsePropertyAccessSyntax]
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.UsePropertyAccessSyntax]
 * [org.jetbrains.kotlin.idea.inspections.MultiFileLocalInspectionTestGenerated]
 */
@ApiStatus.Internal
class UsePropertyAccessSyntaxIntention : SelfTargetingOffsetIndependentIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.messagePointer("use.property.access.syntax")
) {
    override fun isApplicableTo(element: KtExpression): Boolean =
        element.callOrReferenceOrNull(::detectPropertyNameToUseForCall, ::detectPropertyNameToUseForReference) != null

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val propertyName = element.callOrReferenceOrNull(::detectPropertyNameToUseForCall, ::detectPropertyNameToUseForReference) ?: return
        runWriteActionIfPhysical(element) {
            applyTo(element, propertyName, reformat = true)
        }
    }

    fun applyTo(element: KtExpression, propertyName: Name, reformat: Boolean): KtExpression =
        element.callOrReferenceOrNull(
            {
                when (it.valueArguments.size) {
                    0 -> replaceWithPropertyGet(it, propertyName)
                    1 -> replaceWithPropertySet(it, propertyName, reformat)
                    else -> error("More than one argument in call to accessor")
                }
            },
            {
                replaceWithPropertyGet(it.callableReference, propertyName)
            }
        ) ?: error("Can't parse $element (${element::class})")

    private fun detectPropertyNameToUseForReference(referenceExpression: KtCallableReferenceExpression): Name? {
        if (!referenceExpression.languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)) {
            return null
        }
        if (!referenceExpression.callableReference.getReferencedName().startsWith("get")) {
            // Suggest to convert only getters. Keep setters and is-getters untouched
            // Don't suggest replacing setters because setters and property references have different types
            // Don't suggest replacing is-getters because is-getter method reference and is-getter property references have the same syntax
            return null
        }
        if (referenceExpression.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL)
                .get(BindingContext.EXPECTED_EXPRESSION_TYPE, referenceExpression)?.isBuiltinFunctionalType != true
        ) {
            return null
        }

        return (referenceExpression.callableReference.resolveToCall()?.resultingDescriptor as? FunctionDescriptor)
            ?.let {
                @OptIn(FrontendInternals::class)
                findSyntheticProperty(it, referenceExpression.getResolutionFacade().getFrontendService(SyntheticScopes::class.java))
            }
            ?.name
    }

    fun detectPropertyNameToUseForCall(callExpression: KtCallExpression): Name? {
        val qualifiedOrCall = callExpression.getQualifiedExpressionForSelectorOrThis()

        val receiver = (qualifiedOrCall as? KtQualifiedExpression)?.receiverExpression
        if (receiver is KtSuperExpression) return null // cannot call extensions on "super"

        val callee = callExpression.calleeExpression as? KtNameReferenceExpression ?: return null
        val methodName = callee.getReferencedName()
        if (!methodName.isSuitableAsPropertyAccessor()) return null

        val resolutionFacade = callExpression.getResolutionFacade()
        val bindingContext = callExpression.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL_FOR_COMPLETION)

        val isSetUsage = callExpression.valueArguments.size == 1
        val isBodyExpression = (qualifiedOrCall.parent as? KtDeclarationWithBody)?.bodyExpression == qualifiedOrCall
        if (isSetUsage && !isBodyExpression && qualifiedOrCall.isUsedAsExpression(bindingContext)) return null

        val resolvedCall = callExpression.getResolvedCall(bindingContext) ?: return null
        if (!resolvedCall.isReallySuccess()) return null
        val function = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return null

        val inspection = this.inspection as? UsePropertyAccessSyntaxInspection
        val notProperties = inspection?.fqNameList?.toSet() ?: NotPropertiesService.getNotProperties(callExpression)
        if (function.shouldNotConvertToProperty(notProperties)) return null

        val resolutionScope = callExpression.getResolutionScope(bindingContext, resolutionFacade)

        @OptIn(FrontendInternals::class)
        val property = findSyntheticProperty(function, resolutionFacade.getFrontendService(SyntheticScopes::class.java)) ?: return null

        if (KtTokens.KEYWORDS.types.any { it.toString() == property.name.asString() }) return null

        val dataFlowInfo = bindingContext.getDataFlowInfoBefore(callee)
        val expectedType = bindingContext[BindingContext.EXPECTED_EXPRESSION_TYPE, qualifiedOrCall] ?: TypeUtils.NO_EXPECTED_TYPE

        if (!checkWillResolveToProperty(
                resolvedCall,
                property,
                bindingContext,
                resolutionScope,
                dataFlowInfo,
                expectedType,
                resolutionFacade
            )
        ) return null

        val isGetUsage = callExpression.valueArguments.size == 0
        if (isGetUsage) {
            if (methodName.startsWith("is") && function.returnType?.isBoolean() != true) {
                return null
            }
            return property.name
        }

        val valueArgumentExpression = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()?.takeUnless {
            it is KtLambdaExpression || it is KtNamedFunction || it is KtCallableReferenceExpression
        }
        if (valueArgumentExpression == null) return null

        if (callExpression.parent is KtQualifiedExpression && function.returnType?.isUnit() != true) {
            return null
        }

        if (property.type != function.valueParameters.single().type) {
            val qualifiedOrCallCopy = qualifiedOrCall.copied()
            val callExpressionCopy =
                ((qualifiedOrCallCopy as? KtQualifiedExpression)?.selectorExpression ?: qualifiedOrCallCopy) as KtCallExpression
            val newExpression = applyTo(callExpressionCopy, property.name, reformat = false)
            val bindingTrace = DelegatingBindingTrace(bindingContext, "Temporary trace")
            val newBindingContext = newExpression.analyzeInContext(
                resolutionScope,
                contextExpression = callExpression,
                trace = bindingTrace,
                dataFlowInfo = dataFlowInfo,
                expectedType = expectedType,
                isStatement = true
            )

            if (newBindingContext.diagnostics.any { it.severity == Severity.ERROR }) return null
        }

        return property.name
    }

    private fun String.isSuitableAsPropertyAccessor(): Boolean =
        canBePropertyAccessor(this) && commonGetterLikePrefixes.none { prefix -> this.contains(prefix) }

    private fun checkWillResolveToProperty(
        resolvedCall: ResolvedCall<out CallableDescriptor>,
        property: SyntheticJavaPropertyDescriptor,
        bindingContext: BindingContext,
        resolutionScope: LexicalScope,
        dataFlowInfo: DataFlowInfo,
        expectedType: KotlinType,
        facade: ResolutionFacade
    ): Boolean {
        val project = resolvedCall.call.callElement.project
        val newCall = object : DelegatingCall(resolvedCall.call) {
            private val newCallee = KtPsiFactory(project).createExpressionByPattern("$0", property.name, reformat = false)

            override fun getCalleeExpression() = newCallee
            override fun getValueArgumentList(): KtValueArgumentList? = null
            override fun getValueArguments(): List<ValueArgument> = emptyList()
            override fun getFunctionLiteralArguments(): List<LambdaArgument> = emptyList()
        }

        val bindingTrace = DelegatingBindingTrace(bindingContext, "Temporary trace")
        val context = BasicCallResolutionContext.create(
            bindingTrace, resolutionScope, newCall, expectedType, dataFlowInfo,
            ContextDependency.INDEPENDENT, CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
            false, facade.languageVersionSettings,
            facade.dataFlowValueFactory
        )

        @OptIn(FrontendInternals::class)
        val callResolver = facade.frontendService<CallResolver>()
        val result = callResolver.resolveSimpleProperty(context)
        return result.isSuccess && result.resultingDescriptor.original == property
    }

    private fun findSyntheticProperty(function: FunctionDescriptor, syntheticScopes: SyntheticScopes): SyntheticJavaPropertyDescriptor? {
        SyntheticJavaPropertyDescriptor.findByGetterOrSetter(function, syntheticScopes)?.let { return it }

        for (overridden in function.overriddenDescriptors) {
            findSyntheticProperty(overridden, syntheticScopes)?.let { return it }
        }

        return null
    }

    private fun replaceWithPropertyGet(oldElement: KtElement, propertyName: Name): KtExpression {
        val newExpression = KtPsiFactory(oldElement.project).createExpression(propertyName.render())
        return oldElement.replaced(newExpression)
    }

    private fun KtCallExpression.convertExpressionBodyToBlockBodyIfPossible(): KtCallExpression {
        val call = getQualifiedExpressionForSelector() ?: this
        val callParent = call.parent
        if (callParent is KtDeclarationWithBody && call == callParent.bodyExpression) {
            ConvertToBlockBodyIntention.Holder.convert(callParent, true)
            val firstStatement = callParent.bodyBlockExpression?.statements?.first()
            return (firstStatement as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                ?: firstStatement as? KtCallExpression
                ?: throw KotlinExceptionWithAttachments("Unexpected contents of function after conversion: ${callParent::class.java}")
                    .withPsiAttachment("callParent", callParent)
        }
        return this
    }

    private fun replaceWithPropertySet(callExpression: KtCallExpression, propertyName: Name, reformat: Boolean): KtExpression {
        // TODO: consider common case when setter is used as an expression
        val callToConvert = callExpression.convertExpressionBodyToBlockBodyIfPossible()

        val qualifiedExpression = callToConvert.getQualifiedExpressionForSelector()
        val argument = callToConvert.valueArguments.single()

        val psiFactory = KtPsiFactory(callToConvert.project)

        if (qualifiedExpression != null) {
            val pattern = when (qualifiedExpression) {
                is KtDotQualifiedExpression -> "$0.$1=$2"
                is KtSafeQualifiedExpression -> "$0?.$1=$2"
                else -> error(qualifiedExpression) //TODO: make it sealed?
            }

            val newExpression = psiFactory.createExpressionByPattern(
                pattern,
                qualifiedExpression.receiverExpression,
                propertyName,
                argument.getArgumentExpression()!!,
                reformat = reformat
            )
            return qualifiedExpression.replaced(newExpression)
        } else {
            val newExpression = psiFactory.createExpressionByPattern("$0=$1", propertyName, argument.getArgumentExpression()!!)
            return callToConvert.replaced(newExpression)
        }
    }
}

private val commonGetterLikePrefixes: Set<Regex> = setOf(
    "^getOr[A-Z]".toRegex(),
    "^getAnd[A-Z]".toRegex(),
    "^getIf[A-Z]".toRegex(),
)

private inline fun <T> KtExpression.callOrReferenceOrNull(
    call: (KtCallExpression) -> T,
    reference: (KtCallableReferenceExpression) -> T
): T? =
    when (this) {
        is KtCallExpression -> call(this)
        is KtCallableReferenceExpression -> reference(this)
        else -> null
    }
