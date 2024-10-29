// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ResolutionUtils")

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.QualifiedExpressionResolver
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

fun KtElement.getResolutionFacade(): ResolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacade(this)

/**
 * This function throws exception when resolveToDescriptorIfAny returns null, otherwise works equivalently.
 *
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtDeclaration.unsafeResolveToDescriptor(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): DeclarationDescriptor =
    unsafeResolveToDescriptor(getResolutionFacade(), bodyResolveMode)

/**
 * This function first uses declaration resolvers to resolve this declaration and/or additional declarations (e.g. its parent),
 * and then takes the relevant descriptor from binding context.
 * The exact set of declarations to resolve depends on bodyResolveMode
 *
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtDeclaration.resolveToDescriptorIfAny(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL
): DeclarationDescriptor? =
    resolveToDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtClassOrObject.resolveToDescriptorIfAny(bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL) =
    resolveToDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtNamedFunction.resolveToDescriptorIfAny(bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL) =
    resolveToDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtProperty.resolveToDescriptorIfAny(bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL) =
    resolveToDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtParameter.resolveToParameterDescriptorIfAny(bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL) =
    resolveToParameterDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtElement.resolveToCall(bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL) =
    resolveToCall(getResolutionFacade(), bodyResolveMode)

fun ResolvedCall<out CallableDescriptor>.variableCallOrThis(): ResolvedCall<out CallableDescriptor> =
    (this as? VariableAsFunctionResolvedCall)?.variableCall ?: this

fun KtFile.resolveImportReference(fqName: FqName): Collection<DeclarationDescriptor> {
    val facade = getResolutionFacade()
    return facade.resolveImportReference(facade.moduleDescriptor, fqName)
}

fun KtAnnotationEntry.resolveToDescriptorIfAny(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.PARTIAL_NO_ADDITIONAL
): AnnotationDescriptor? =
    resolveToDescriptorIfAny(getResolutionFacade(), bodyResolveMode)

// This and next functions are used for 'normal' element analysis
// This analysis *should* provide all information extractable from this KtElement except:
// - for declarations, it does not analyze their bodies
// - for classes, it does not analyze their content
// - for member / top-level properties, it does not analyze initializers / accessors
// This information includes related descriptors, resolved calls (but not inside body, see above!)
// and many other binding context slices.
// Normally, the function is used on local declarations or statements / expressions
// Any usage on non-local declaration is a bit suspicious,
// consider replacing it with resolveToDescriptorIfAny and
// remember that body / content is not analyzed;
// if it's necessary, use analyzeWithContent()
//
// If you need diagnostics in result context, use BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS.
// BodyResolveMode.FULL analyzes all statements on the level of KtElement and above.
// BodyResolveMode.PARTIAL analyzes only statements necessary for this KtElement precise analysis.
//
// See also: ResolveSessionForBodies, ResolveElementCache
/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
@JvmOverloads
fun KtElement.analyze(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext = analyze(getResolutionFacade(), bodyResolveMode)

@JvmOverloads
fun KtElement.safeAnalyze(
    bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
): BindingContext = safeAnalyze(getResolutionFacade(), bodyResolveMode)

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtElement.analyzeAndGetResult(): AnalysisResult = analyzeAndGetResult(getResolutionFacade())

/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtElement.analyzeWithContentAndGetResult(): AnalysisResult = analyzeWithContentAndGetResult(getResolutionFacade())

fun KtElement.findModuleDescriptor(): ModuleDescriptor = getResolutionFacade().moduleDescriptor

// This function is used on declarations to make analysis not only declaration itself but also it content:
// body for declaration with body, initializer & accessors for properties
/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
fun KtDeclaration.analyzeWithContent(): BindingContext = analyzeWithContent(getResolutionFacade())

// This function is used to make full analysis of declaration container.
// All its declarations, including their content (see above), are analyzed.
/**
 * **Please, use overload with providing resolutionFacade for stable results of subsequent calls**
 */
inline fun <reified T> T.analyzeWithContent(): BindingContext where T : KtDeclarationContainer, T : KtElement =
    analyzeWithContent(getResolutionFacade())

/**
 * This function is expected to produce the same result as compiler for the whole file content (including diagnostics,
 * trace slices, descriptors, etc.).
 *
 * It's not recommended to call this function without real need.
 *
 * @ref [KotlinCacheService]
 * @ref [org.jetbrains.kotlin.idea.caches.resolve.PerFileAnalysisCache]
 */
fun KtFile.analyzeWithAllCompilerChecks(vararg extraFiles: KtFile): AnalysisResult = this.analyzeWithAllCompilerChecks(null, *extraFiles)

fun KtFile.analyzeWithAllCompilerChecks(callback: ((Diagnostic) -> Unit)?, vararg extraFiles: KtFile): AnalysisResult {
    return if (extraFiles.isEmpty()) {
        KotlinCacheService.getInstance(project).getResolutionFacade(this)
            .analyzeWithAllCompilerChecks(this, callback)
    } else {
        KotlinCacheService.getInstance(project).getResolutionFacade(listOf(this) + extraFiles.toList())
            .analyzeWithAllCompilerChecks(this, callback)
    }
}

/**
 * This function is expected to produce the same result as compiler for the given element and its children (including diagnostics,
 * trace slices, descriptors, etc.). For some expression element it actually performs analyze for some parent (usually declaration).
 *
 * It's not recommended to call this function without real need.
 *
 * NB: for statements / expressions, usually should be replaced with analyze(),
 * for declarations, analyzeWithContent() will do what you want.
 *
 * @ref [KotlinCacheService]
 * @ref [org.jetbrains.kotlin.idea.caches.resolve.PerFileAnalysisCache]
 */
@ApiStatus.Internal
@Deprecated(
    "Use either KtFile.analyzeWithAllCompilerChecks() or KtElement.analyzeAndGetResult()",
    ReplaceWith("analyzeAndGetResult()")
)
fun KtElement.analyzeWithAllCompilerChecks(): AnalysisResult = getResolutionFacade().analyzeWithAllCompilerChecks(this)

// this method don't check visibility and collect all descriptors with given fqName
@OptIn(FrontendInternals::class)
fun ResolutionFacade.resolveImportReference(
    moduleDescriptor: ModuleDescriptor,
    fqName: FqName
): Collection<DeclarationDescriptor> {
    val importDirective = KtPsiFactory(project).createImportDirective(ImportPath(fqName, false))
    val qualifiedExpressionResolver = this.getFrontendService(QualifiedExpressionResolver::class.java)
    return qualifiedExpressionResolver.processImportReference(
        importDirective,
        moduleDescriptor,
        BindingTraceContext(project),
        excludedImportNames = emptyList(),
        packageFragmentForVisibilityCheck = null
    )?.getContributedDescriptors() ?: emptyList()
}

fun KtReferenceExpression.resolveMainReference(): PsiElement? =
    try {
        mainReference.resolve()
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        throw KotlinExceptionWithAttachments("Unable to resolve reference", e)
            .withPsiAttachment("reference.txt", this)
            .withPsiAttachment("file.kt", containingFile)
    }
