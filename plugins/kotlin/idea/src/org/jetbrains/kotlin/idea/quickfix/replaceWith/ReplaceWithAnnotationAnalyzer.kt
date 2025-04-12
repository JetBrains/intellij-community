// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.openapi.diagnostic.ControlFlowException
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.core.unwrapIfFakeOverride
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.descriptors.ClassResolutionScopesSupport
import org.jetbrains.kotlin.resolve.scopes.*
import org.jetbrains.kotlin.resolve.scopes.utils.chainImportingScopes
import org.jetbrains.kotlin.resolve.scopes.utils.memberScopeAsImportingScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices

@OptIn(FrontendInternals::class)
object ReplaceWithAnnotationAnalyzer {
    fun analyzeCallableReplacement(
        replaceWith: ReplaceWithData,
        symbolDescriptor: CallableDescriptor,
        resolutionFacade: ResolutionFacade,
        reformat: Boolean
    ): CodeToInline? {
        val originalDescriptor = symbolDescriptor.unwrapIfFakeOverride().original
        return analyzeOriginal(replaceWith, originalDescriptor, resolutionFacade, reformat)
    }

    private fun analyzeOriginal(
        replaceWith: ReplaceWithData,
        symbolDescriptor: CallableDescriptor,
        resolutionFacade: ResolutionFacade,
        reformat: Boolean
    ): CodeToInline? {
        val psiFactory = KtPsiFactory(resolutionFacade.project)
        val expression = psiFactory.createExpressionIfPossible(replaceWith.pattern) ?: return null

        val scope = buildScope(resolutionFacade, replaceWith, symbolDescriptor) ?: return null

        val expressionTypingServices = resolutionFacade.getFrontendService(ExpressionTypingServices::class.java)

        fun analyzeExpression(ignore: KtExpression) = expression.analyzeInContext(
            scope,
            expressionTypingServices = expressionTypingServices
        )

        return CodeToInlineBuilder(symbolDescriptor, resolutionFacade, originalDeclaration = null).prepareCodeToInline(
            expression,
            emptyList(),
            ::analyzeExpression,
            reformat,
        )
    }

    fun analyzeClassifierReplacement(
        replaceWith: ReplaceWithData,
        symbolDescriptor: ClassifierDescriptorWithTypeParameters,
        resolutionFacade: ResolutionFacade
    ): KtUserType? {
        val psiFactory = KtPsiFactory(resolutionFacade.project)
        val typeReference = try {
            psiFactory.createType(replaceWith.pattern)
        } catch (e: Exception) {
            if (e is ControlFlowException) throw e
            return null
        }
        if (typeReference.typeElement !is KtUserType) return null

        val scope = buildScope(resolutionFacade, replaceWith, symbolDescriptor) ?: return null

        val typeResolver = resolutionFacade.getFrontendService(TypeResolver::class.java)
        val bindingTrace = BindingTraceContext(resolutionFacade.project)
        typeResolver.resolvePossiblyBareType(TypeResolutionContext(scope, bindingTrace, false, true, false), typeReference)

        val typesToQualify = ArrayList<Pair<KtNameReferenceExpression, FqName>>()

        typeReference.forEachDescendantOfType<KtNameReferenceExpression> { expression ->
            val parentType = expression.parent as? KtUserType ?: return@forEachDescendantOfType
            if (parentType.qualifier != null) return@forEachDescendantOfType
            val targetClass = bindingTrace.bindingContext[BindingContext.REFERENCE_TARGET, expression] as? ClassDescriptor
                ?: return@forEachDescendantOfType
            val fqName = targetClass.fqNameUnsafe
            if (fqName.isSafe) {
                typesToQualify.add(expression to fqName.toSafe())
            }
        }

        for ((nameExpression, fqName) in typesToQualify) {
            nameExpression.mainReference.bindToFqName(fqName, KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
        }

        return typeReference.typeElement as KtUserType
    }

    private fun buildScope(
        resolutionFacade: ResolutionFacade,
        replaceWith: ReplaceWithData,
        symbolDescriptor: DeclarationDescriptor
    ): LexicalScope? {
        val module = resolutionFacade.moduleDescriptor
        val explicitImportsScope = buildExplicitImportsScope(importFqNames(replaceWith), resolutionFacade, module)
        val languageVersionSettings = resolutionFacade.languageVersionSettings
        val defaultImportsScopes = buildDefaultImportsScopes(resolutionFacade, module)

        return getResolutionScope(
            symbolDescriptor, symbolDescriptor, listOf(explicitImportsScope), defaultImportsScopes, languageVersionSettings
        )
    }

    private fun buildDefaultImportsScopes(
        resolutionFacade: ResolutionFacade,
        module: ModuleDescriptor
    ): List<ImportingScope> {
        val allDefaultImports =
            resolutionFacade.frontendService<TargetPlatform>().findAnalyzerServices(resolutionFacade.project)
                .getDefaultImports(includeLowPriorityImports = true)
        val (allUnderImports, aliasImports) = allDefaultImports.partition { it.isAllUnder }
        // this solution doesn't support aliased default imports with a different alias
        // TODO: Create import directives from ImportPath, create ImportResolver, create LazyResolverScope, see FileScopeProviderImpl

        return listOf(buildExplicitImportsScope(aliasImports.map { it.fqName }, resolutionFacade, module)) +
                allUnderImports.map { module.getPackage(it.fqName).memberScope.memberScopeAsImportingScope() }.asReversed()
    }

    private fun buildExplicitImportsScope(
        importFqNames: List<FqName>,
        resolutionFacade: ResolutionFacade,
        module: ModuleDescriptor
    ): ExplicitImportsScope {
        val importedSymbols = importFqNames.flatMap { resolutionFacade.resolveImportReference(module, it) }
        return ExplicitImportsScope(importedSymbols)
    }

    private fun importFqNames(annotation: ReplaceWithData): List<FqName> {
        val result = ArrayList<FqName>()
        for (fqName in annotation.imports) {
            if (!FqNameUnsafe.isValid(fqName)) continue
            result += FqNameUnsafe(fqName).takeIf { it.isSafe }?.toSafe() ?: continue
        }
        return result
    }

    private fun getResolutionScope(
        descriptor: DeclarationDescriptor,
        ownerDescriptor: DeclarationDescriptor,
        explicitScopes: Collection<ExplicitImportsScope>,
        additionalScopes: Collection<ImportingScope>,
        languageVersionSettings: LanguageVersionSettings
    ): LexicalScope? {
        return when (descriptor) {
            is PackageFragmentDescriptor -> {
                val moduleDescriptor = descriptor.containingDeclaration
                getResolutionScope(
                    moduleDescriptor.getPackage(descriptor.fqName),
                    ownerDescriptor,
                    explicitScopes,
                    additionalScopes,
                    languageVersionSettings
                )
            }

            is PackageViewDescriptor -> {
                val memberAsImportingScope = descriptor.memberScope.memberScopeAsImportingScope()
                LexicalScope.Base(
                    chainImportingScopes(explicitScopes + listOf(memberAsImportingScope) + additionalScopes)!!,
                    ownerDescriptor
                )
            }

            is ClassDescriptor -> {
                val outerScope = getResolutionScope(
                    descriptor.containingDeclaration, ownerDescriptor, explicitScopes, additionalScopes, languageVersionSettings
                ) ?: return null
                ClassResolutionScopesSupport(
                    descriptor,
                    LockBasedStorageManager.NO_LOCKS,
                    languageVersionSettings
                ) { outerScope }.scopeForMemberDeclarationResolution()
            }

            is TypeAliasDescriptor -> {
                val outerScope = getResolutionScope(
                    descriptor.containingDeclaration, ownerDescriptor, explicitScopes, additionalScopes, languageVersionSettings
                ) ?: return null
                LexicalScopeImpl(
                    outerScope,
                    descriptor,
                    false,
                    null,
                    emptyList(),
                    LexicalScopeKind.TYPE_ALIAS_HEADER,
                    LocalRedeclarationChecker.DO_NOTHING
                ) {
                    for (typeParameter in descriptor.declaredTypeParameters) {
                        addClassifierDescriptor(typeParameter)
                    }
                }
            }

            is FunctionDescriptor -> {
                val outerScope = getResolutionScope(
                    descriptor.containingDeclaration, ownerDescriptor, explicitScopes, additionalScopes, languageVersionSettings
                ) ?: return null
                FunctionDescriptorUtil.getFunctionInnerScope(outerScope, descriptor, LocalRedeclarationChecker.DO_NOTHING)
            }

            is PropertyDescriptor -> {
                val outerScope = getResolutionScope(
                    descriptor.containingDeclaration, ownerDescriptor, explicitScopes, additionalScopes, languageVersionSettings
                ) ?: return null
                val propertyHeader = ScopeUtils.makeScopeForPropertyHeader(outerScope, descriptor)
                LexicalScopeImpl(
                    propertyHeader,
                    descriptor,
                    false,
                    descriptor.extensionReceiverParameter,
                    descriptor.contextReceiverParameters,
                    LexicalScopeKind.PROPERTY_ACCESSOR_BODY
                )
            }

            else -> return null // something local, should not work with ReplaceWith
        }
    }

}
