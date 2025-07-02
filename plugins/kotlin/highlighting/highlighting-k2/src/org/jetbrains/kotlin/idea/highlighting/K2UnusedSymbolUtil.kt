// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.siyeh.ig.psiutils.SerializationUtils
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isCheapEnoughToSearchUsages
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExplicitlyIgnoredByName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.idea.searching.inheritors.hasAnyInheritors
import org.jetbrains.kotlin.idea.searching.inheritors.hasAnyOverridings
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.scripting.definitions.isScript

object K2UnusedSymbolUtil {
    private val KOTLIN_ADDITIONAL_ANNOTATIONS: List<String> = listOf("kotlin.test.*", "kotlin.js.JsExport")
    private val DEPRECATION_LEVEL_HIDDEN: FqName = StandardNames.FqNames.deprecationLevel.child(Name.identifier("HIDDEN"))
    private val DEPRECATION_LEVEL_PARAMETER_NAME: Name = Name.identifier("level")

    // Simple PSI-based checks
    fun isApplicableByPsi(declaration: KtNamedDeclaration): Boolean {
        if (declaration.containingFile.isScript()) return false
        // never mark companion object as unused (there are too many reasons it can be needed for)
        if (declaration is KtObjectDeclaration && declaration.isCompanion()) return false

        if (declaration is KtParameter) {
            // nameless parameters like `(Type) -> Unit` or `_` make no sense to highlight
            if (declaration.isExplicitlyIgnoredByName()) return false
            // functional type params like `fun foo(u: (usedParam: Type) -> Unit)` shouldn't be highlighted because they could be implicitly used by lambda arguments
            if (declaration.isFunctionTypeParameter) return false
            val ownerFunction = declaration.ownerDeclaration
            if (ownerFunction is KtConstructor<*>) {
                // constructor parameters of data class are considered used because they are implicitly used in equals() (???)
                val containingClass = declaration.containingClass()
                if (containingClass != null) {
                    if (containingClass.isData()) return false
                    // constructor parameters-fields of value class are considered used because they are implicitly used in equals() (???)
                    if (containingClass.isValue() && declaration.hasValOrVar()) return false
                    // constructor parameters-fields of inline class are considered used because they are implicitly used in equals() (???)
                    if (containingClass.isInline() && declaration.hasValOrVar()) return false
                    if (isExpectedOrActual(containingClass)) return false
                }
            } else if (ownerFunction is KtFunction) {
                if (ownerFunction.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
                    // operator parameters are hardcoded to be used since they can't be removed at will, because operator convention would break
                    return false
                }
                if (isEffectivelyAbstractFunction(ownerFunction) || isExpectedOrActual(ownerFunction)) {
                    return false
                }

                val containingClass = ownerFunction.containingClassOrObject
                if (containingClass != null && isExpectedOrActual(containingClass)) {
                    return false
                }
            }
        }
        val owner: KtNamedDeclaration
        if (declaration is KtTypeParameter) {
            var parent = declaration.parent
            if (parent != null && parent !is KtTypeParameterListOwner) parent = parent.parent
            owner = parent as? KtTypeParameterListOwner ?: declaration
        } else {
            owner = declaration
        }
        return !owner.hasModifier(KtTokens.OVERRIDE_KEYWORD)
    }

    private fun isExpectedOrActual(owner: KtModifierListOwner): Boolean {
        val modifierList = owner.modifierList
        return modifierList != null && (
                modifierList.hasModifier(KtTokens.EXPECT_KEYWORD) || modifierList.hasModifier(KtTokens.ACTUAL_KEYWORD))
    }

    private fun isEffectivelyAbstractFunction(ownerFunction: KtFunction): Boolean {
        val modifierList = ownerFunction.modifierList
        if (modifierList != null && (modifierList.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                    || modifierList.hasModifier(KtTokens.EXPECT_KEYWORD)
                    || modifierList.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                    || modifierList.hasModifier(KtTokens.OPEN_KEYWORD))
        ) { // maybe one of the overriders does use this parameter
            return true
        }
        return ownerFunction.containingClass()?.isInterface() == true
    }

    context(KaSession)
    fun isHiddenFromResolution(declaration: KtNamedDeclaration): Boolean {
        val anno = declaration.findAnnotation(
            StandardClassIds.Annotations.Deprecated,
            useSiteTarget = null,
            withResolve = false,
        ) ?: return false
        val call = anno.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val levelArgument = call.argumentMapping.entries.find { it.value.symbol.name == DEPRECATION_LEVEL_PARAMETER_NAME } ?: return false
        val levelArgumentCall = levelArgument.key.resolveToCall()?.successfulVariableAccessCall() ?: return false
        return levelArgumentCall.symbol.importableFqName == DEPRECATION_LEVEL_HIDDEN
    }

    fun isLocalDeclaration(declaration: KtNamedDeclaration): Boolean {
        if (declaration is KtProperty && declaration.isLocal) return true
        return declaration is KtParameter && !(declaration.parent.parent is KtPrimaryConstructor && declaration.hasValOrVar())
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getPsiToReportProblem(declaration: KtNamedDeclaration, isJavaEntryPointInspection: UnusedDeclarationInspectionBase): PsiElement? {
        if (((declaration as? KtParameter)?.parent?.parent as? KtModifierListOwner)?.hasModifier(KtTokens.EXTERNAL_KEYWORD) == true) {
            return null
        }
        val symbol = declaration.symbol
        if (declaration.languageVersionSettings.getFlag(
                AnalysisFlags.explicitApiMode
            ) != ExplicitApiMode.DISABLED && symbol.compilerVisibility.isPublicAPI
        ) {
            return null
        }
        if (symbol is KaNamedFunctionSymbol && symbol.isOperator && declaration.name?.let { DataClassResolver.isComponentLike(it) } != true) {
            return null
        }

        val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) {
            isCheapEnoughToSearchUsages(declaration)
        }
        if (isEntryPoint(declaration, isCheapEnough, isJavaEntryPointInspection)) return null
        if (declaration.isFinalizeMethod()) return null
        if (declaration is KtProperty && declaration.isSerializationImplicitlyUsedField()) return null
        if (declaration is KtNamedFunction && declaration.isSerializationImplicitlyUsedMethod()) return null
        // properties can be referred by `component1`/`component2`, which is too expensive to search, don't mark them as unused
        val declarationContainingClass = declaration.containingClass()
        if (declaration.isConstructorDeclaredProperty() &&
            declarationContainingClass?.mustHaveNonEmptyPrimaryConstructor() == true
        ) return null
        // experimental annotations
        if (symbol is KaClassSymbol && symbol.classKind == KaClassKind.ANNOTATION_CLASS) {
            val fqName = symbol.nameOrAnonymous.asString()
            val languageVersionSettings = declaration.languageVersionSettings
            if (fqName in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return null
        }

        // Main checks: finding reference usages && text usages
        if (hasNonTrivialUsages(declaration, declarationContainingClass, isCheapEnough, symbol)) return null
        if (declaration is KtClassOrObject && classOrObjectHasTextUsages(declaration)) return null

        return declaration.nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword()
    }

    context(KaSession)
    private fun KtDeclaration.hasKotlinAdditionalAnnotation(): Boolean =
        this is KtNamedDeclaration && checkAnnotatedUsingPatterns(this, KOTLIN_ADDITIONAL_ANNOTATIONS)

    private fun KtProperty.isSerializationImplicitlyUsedField(): Boolean {
        val ownerObject = getNonStrictParentOfType<KtClassOrObject>() as? KtObjectDeclaration ?: return false
        val lightClass = if (ownerObject.isCompanion()) {
            ownerObject.getNonStrictParentOfType<KtClass>()?.toLightClass()
        } else {
            ownerObject.toLightClass()
        } ?: return false
        return lightClass.fields.any { it.name == name && SerializationUtils.isSerializationImplicitlyUsedField(it) }
    }

    private fun KtNamedFunction.isSerializationImplicitlyUsedMethod(): Boolean =
        toLightMethods().any { JavaHighlightUtil.isSerializationRelatedMethod(it, it.containingClass) }


    private fun isAnnotationParameter(parameter: KtParameter): Boolean {
        val constructor = parameter.ownerFunction as? KtConstructor<*> ?: return false
        return constructor.containingClassOrObject?.isAnnotation() ?: false
    }

    // variation of IDEA's AnnotationUtil.checkAnnotatedUsingPatterns()
    context(KaSession)
    fun checkAnnotatedUsingPatterns(declaration: KtNamedDeclaration, annotationPatterns: Collection<String>): Boolean {
        if (declaration.annotationEntries.isEmpty()) return false
        val annotationsPresent = declaration.annotationEntries.mapNotNull {
            val reference = it?.calleeExpression?.constructorReferenceExpression?.mainReference ?: return@mapNotNull null
            val symbol = reference.resolveToSymbol() ?: return@mapNotNull null
            val constructorSymbol = symbol as? KaConstructorSymbol ?: return@mapNotNull null
            constructorSymbol.containingClassId?.asSingleFqName()?.asString()
        }
        if (annotationsPresent.isEmpty()) return false

        for (pattern in annotationPatterns) {
            val hasAnnotation = if (pattern.endsWith(".*")) {
                annotationsPresent.any { it.startsWith(pattern.dropLast(1)) }
            } else {
                pattern in annotationsPresent
            }
            if (hasAnnotation) return true
        }

        return false
    }

    context(KaSession)
    private fun checkDeclaration(declaration: KtNamedDeclaration, importedDeclaration: KtNamedDeclaration): Boolean =
        declaration !in importedDeclaration.parentsWithSelf && !hasNonTrivialUsages(
            importedDeclaration,
            declarationContainingClass = importedDeclaration.containingClass()
        )

    context(KaSession)
    private fun hasNonTrivialUsages(
        declaration: KtNamedDeclaration,
        declarationContainingClass: KtClass?,
        symbol: KaDeclarationSymbol? = null
    ): Boolean {
        val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) { isCheapEnoughToSearchUsages(declaration) }
        return hasNonTrivialUsages(declaration, declarationContainingClass, isCheapEnough, symbol)
    }

    context(KaSession)
    private fun hasNonTrivialUsages(
        declaration: KtNamedDeclaration,
        declarationContainingClass: KtClass?,
        isCheapEnough: Lazy<PsiSearchHelper.SearchCostResult>,
        symbol: KaDeclarationSymbol? = null
    ): Boolean {
        val project = declaration.project
        val psiSearchHelper = PsiSearchHelper.getInstance(project)

        val useScope = psiSearchHelper.getUseScope(declaration)
        val restrictedScope = if (useScope is GlobalSearchScope) {
            val zeroOccurrences = when (isCheapEnough.value) {
                PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> true
                PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES -> false
                PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES -> return true // searching usages is too expensive; behave like it is used
            }

            if (zeroOccurrences && !declaration.hasActualModifier()) {
                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                    // go on: the companion object can be used only in containing class
                } else {
                    return false
                }
            }
            if (declaration.hasActualModifier()) {
                KotlinSourceFilterScope.projectSourcesAndResources(project.projectScope(), project)
            } else {
                KotlinSourceFilterScope.projectSourcesAndResources(useScope, project)
            }
        } else useScope

        if (declaration is KtTypeParameter) {
            if (declarationContainingClass != null) {
                val isOpenClass = declarationContainingClass.isInterface()
                        || declarationContainingClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                        || declarationContainingClass.hasModifier(KtTokens.SEALED_KEYWORD)
                        || declarationContainingClass.hasModifier(KtTokens.OPEN_KEYWORD)
                if (isOpenClass && hasOverrides(declarationContainingClass)) return true

                val containingClassSearchScope = GlobalSearchScope.projectScope(project)
                val isRequiredToCallFunction =
                    referenceExists(declarationContainingClass, containingClassSearchScope) { ref ->
                        val userType = ref.element.parent as? KtUserType ?: return@referenceExists false
                        val typeArguments = userType.typeArguments
                        if (typeArguments.isEmpty()) return@referenceExists false

                        val parameter = userType.getStrictParentOfType<KtParameter>() ?: return@referenceExists false
                        val callableDeclaration = parameter.getStrictParentOfType<KtCallableDeclaration>()?.let {
                            it as? KtNamedFunction ?: it.containingClass()
                        } ?: return@referenceExists false
                        val typeParameters = callableDeclaration.typeParameters.map { it.name }
                        if (typeParameters.isEmpty()) return@referenceExists false
                        if (typeArguments.none { it.text in typeParameters }) return@referenceExists false

                        referenceExists(callableDeclaration, containingClassSearchScope) {
                            val callElement = it.element.parent as? KtCallElement
                            callElement != null && callElement.typeArgumentList == null
                        }
                    }
                if (isRequiredToCallFunction) return true
            }
        }

        return (declaration is KtObjectDeclaration && declaration.isCompanion() &&
                declaration.body?.declarations?.isNotEmpty() == true) ||
                hasOverrides(declaration) ||
                hasReferences(project, declaration, declarationContainingClass, symbol, restrictedScope) ||
                hasFakeOverrides(declaration, restrictedScope) ||
                hasPlatformImplementations(declaration)
    }

    private val KtNamedDeclaration.isObjectOrEnum: Boolean get() = this is KtObjectDeclaration || this is KtClass && isEnum()

    context(KaSession)
    private fun checkReference(refElement: PsiElement, declaration: KtNamedDeclaration, originalDeclaration: KtNamedDeclaration?): Boolean {
        if (declaration.isAncestor(refElement)) return true // usages inside element's declaration are not counted

        if (refElement.parent is KtValueArgumentName) return true // usage of parameter in the form of named argument is not counted

        val import = refElement.getParentOfType<KtImportDirective>(false) ?: return false
        val aliasName = import.aliasName
        if (aliasName != null && aliasName != declaration.name) {
            return false
        }
        // check if we import member(s) from object / nested object / enum and search for their usages
        if (declaration !is KtClassOrObject && originalDeclaration !is KtClassOrObject) return true
        if (import.isAllUnder) {
            val importedFrom = import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve()
                    as? KtClassOrObject ?: return true
            return importedFrom.declarations.none {
                it is KtNamedDeclaration && hasNonTrivialUsages(it, declarationContainingClass = it.containingClass())
            }
        }
        val importedFqName = import.importedFqName
        val declarationFqName = declaration.fqName
        if (importedFqName == declarationFqName) return true
        if (declarationFqName != null && importedFqName?.startsWith(declarationFqName) == true) {
            // imported a member of declaration
            return false
        }
        val importedDeclaration =
            import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() as? KtNamedDeclaration
                ?: return true

        if (declaration.isObjectOrEnum || importedDeclaration.containingClassOrObject is KtObjectDeclaration) {
            return checkDeclaration(declaration, importedDeclaration)
        }

        if (originalDeclaration?.isObjectOrEnum == true) {
            return checkDeclaration(originalDeclaration, importedDeclaration)
        }

        return true
    }

    context(KaSession)
    private fun hasReferences(
        project: Project,
        declaration: KtNamedDeclaration,
        declarationContainingClass: KtClass?,
        symbol: KaDeclarationSymbol?,
        useScope: SearchScope
    ): Boolean {
        val originalDeclaration = (symbol as? KaTypeAliasSymbol)?.expandedType?.expandedSymbol?.psi as? KtNamedDeclaration
        if (symbol !is KaNamedFunctionSymbol || !symbol.annotations.contains(JvmStandardClassIds.Annotations.JvmName)) {
            val symbolPointer = symbol?.createPointer()
            if (declaration is KtSecondaryConstructor &&
                declarationContainingClass != null &&
                // when too many occurrences of this class, consider it used
                (isCheapEnoughToSearchUsages(declarationContainingClass) == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
                        || referenceExists(declarationContainingClass, useScope) {
                            val refElement = it.element
                            refElement is KtElement && analyze(refElement) {
                                refElement.getStrictParentOfType<KtTypeAlias>() != null // ignore unusedness of type aliased classes - they are too hard to trace
                                        || refElement.getStrictParentOfType<KtCallExpression>()?.resolveToCall()
                                    ?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol == symbolPointer?.restoreSymbol()
                            }
                        })
            ) {
                return true
            }
            if (declaration is KtCallableDeclaration && declaration.canBeHandledByLightMethods(symbol)) {
                val lightMethods = declaration.toLightMethods()
                if (lightMethods.isNotEmpty()) {
                    val lightMethodsUsed = lightMethods.any { method ->
                        isTooManyOccurrencesToCheck(method, declaration, project) || !MethodReferencesSearch.search(method)
                            .forEach(Processor {
                                val checkReference = checkReference(it.element, declaration, originalDeclaration)
                                checkReference
                            })
                    }
                    if (lightMethodsUsed) return true
                    if (!declaration.hasActualModifier()) return false
                }
            }

            if (declaration is KtEnumEntry) {
                val enumClass = declarationContainingClass?.takeIf { it.isEnum() }
                if (hasBuiltInEnumFunctionReference(enumClass, useScope)) return true
            }
        }

        val handler = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager.getFindUsagesHandler(declaration, true)
        if (handler != null) {
            val options = handler.findUsagesOptions
            // effectively disable search for text occurrences for classes which are processed earlier but faster
            options.isSearchForTextOccurrences = false
            val result = handler.processElementUsages(declaration, Processor {
                val refElement = it.element
                refElement == null || checkReference(refElement, declaration, originalDeclaration)
            }, options)
            if (!result) {
                return true
            }
        }
        return checkPrivateDeclaration(declaration, symbol, originalDeclaration)
    }

    private fun isTooManyOccurrencesToCheck(
        method: PsiMethod,
        declaration: KtCallableDeclaration,
        project: Project
    ): Boolean {
        val searchScope = method.useScope
        val name = method.name
        return !declaration.name.equals(name) && searchScope is GlobalSearchScope &&
                PsiSearchHelper.getInstance(project).isCheapEnoughToSearch(name, searchScope, null) == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
    }

    /**
     * Return true if [declaration] is a private nested class or object referenced by an import directive and the target symbol of
     * the import directive is used by other references.
     *
     * Note that we need this function to handle the case [declaration] is not directly referenced by any expressions other than an import
     * directive, but the import directive target is used. For example,
     *
     *   import C.CC.value
     *   class C {
     *     fun value() = value
     *     private object CC<caret> {
     *         const val value = 3
     *     }
     *   }
     *
     * In the above code, CC is not referenced by any expressions other than `import C.CC.value`,
     * but `C.CC.value` is used by `fun value() = value`, so we cannot delete `import C.CC.value`, and we have to keep CC.
     */
    context(KaSession)
    private fun checkPrivateDeclaration(
        declaration: KtNamedDeclaration,
        symbol: KaDeclarationSymbol?,
        originalDeclaration: KtNamedDeclaration?
    ): Boolean {
        if (symbol == null || !declaration.isPrivateNestedClassOrObject) return false

        val setOfImportedDeclarations = hashSetOf<KtSimpleNameExpression>()
        declaration.containingKtFile.importList?.acceptChildren(simpleNameExpressionRecursiveVisitor {
            setOfImportedDeclarations += it
        })

        return setOfImportedDeclarations.mapNotNull { it.referenceExpression() }
            .filter { symbol in it.mainReference.resolveToSymbols() }
            .any { !checkReference(it.mainReference.element, declaration, originalDeclaration) }
    }

    // search for references to an element in the scope, satisfying predicate, lazily
    private fun referenceExists(psiElement: PsiElement, scope: SearchScope, predicate: (PsiReference) -> Boolean): Boolean {
        return !ReferencesSearch.search(KotlinReferencesSearchParameters(psiElement, scope))
            .forEach(Processor { !predicate.invoke(it) })
    }

    context(KaSession)
    private fun hasBuiltInEnumFunctionReference(enumClass: KtClass?, useScope: SearchScope): Boolean {
        if (enumClass == null) return false
        val isFoundEnumFunctionReferenceViaSearch = referenceExists(enumClass, useScope) {
            val ktElement = it.element as? KtElement ?: return@referenceExists false
            analyze(ktElement) {
                hasBuiltInEnumFunctionReference(it, enumClass)
            }
        }

        return isFoundEnumFunctionReferenceViaSearch || hasEnumFunctionReferenceInEnumClass(enumClass)
    }

    context(KaSession)
    private fun KtSimpleNameExpression.isReferenceToBuiltInEnumEntries(): Boolean =
        isEnumValuesSoftDeprecateEnabled() && this.getReferencedNameAsName() == StandardNames.ENUM_ENTRIES && isSynthesizedFunction()

    /**
     * Checks calls inside the enum class without receiver expression. Example: `values()`, `::values`
     */
    context(KaSession)
    private fun hasEnumFunctionReferenceInEnumClass(enumClass: KtClass): Boolean {
        val isFoundCallableReference = enumClass.anyDescendantOfType<KtCallableReferenceExpression> {
            it.receiverExpression == null && it.containingClass() == enumClass && it.isReferenceToBuiltInEnumFunction()
        }
        if (isFoundCallableReference) return true

        val isFoundSimpleNameExpression = enumClass.anyDescendantOfType<KtSimpleNameExpression> {
            it.parent !is KtCallableReferenceExpression && it.containingClass() == enumClass
                    && it.getQualifiedExpressionForSelector() == null && it.isReferenceToBuiltInEnumEntries()
        }
        if (isFoundSimpleNameExpression) return true

        return enumClass.anyDescendantOfType<KtCallExpression> {
            it.getQualifiedExpressionForSelector() == null && it.containingClass() == enumClass && it.isReferenceToBuiltInEnumFunction()
        }
    }

    /**
     * Checks calls in enum class with explicit receiver expression. Example: EnumClass.values(), EnumClass::values.
     * Also includes search by imports and kotlin.enumValues, kotlin.enumValueOf functions
     */
    context(KaSession)
    private fun hasBuiltInEnumFunctionReference(reference: PsiReference, enumClass: KtClass): Boolean {
        val parent = reference.element.parent
        if (parent is KtQualifiedExpression) {
            if (parent
                    .normalizeEnumQualifiedExpression(enumClass)
                    ?.canBeReferenceToBuiltInEnumFunction() == true
            ) return true

            if (parent
                    .normalizeEnumCallableReferenceExpression(enumClass)
                    ?.canBeReferenceToBuiltInEnumFunction() == true
            ) return true
        }
        if ((parent as? KtCallableReferenceExpression)?.canBeReferenceToBuiltInEnumFunction() == true) return true
        if (((parent as? KtTypeElement)?.parent as? KtTypeReference)?.isReferenceToBuiltInEnumFunction() == true) return true
        if ((parent as? PsiImportStaticReferenceElement)?.isReferenceToBuiltInEnumFunction() == true) return true
        if ((parent as? PsiReferenceExpression)?.isReferenceToBuiltInEnumFunction(enumClass) == true) return true
        if ((parent as? KtElement)?.normalizeImportDirective()?.isUsedStarImportOfEnumStaticFunctions() == true) return true
        return (parent as? PsiImportStaticStatement)?.isUsedStarImportOfEnumStaticFunctions() == true
    }

    private fun KtElement.normalizeImportDirective(): KtImportDirective? {
        if (this is KtImportDirective) return this
        return this.parent as? KtImportDirective
    }

    private fun KtQualifiedExpression.normalizeEnumQualifiedExpression(enumClass: KtClass): KtQualifiedExpression? {
        if (receiverExpression.text == enumClass.name) return this
        if (this.selectorExpression?.text == enumClass.name) return this.parent as? KtQualifiedExpression
        return null
    }

    private fun KtQualifiedExpression.normalizeEnumCallableReferenceExpression(enumClass: KtClass): KtCallableReferenceExpression? {
        if (this.selectorExpression?.text == enumClass.name) return this.parent as? KtCallableReferenceExpression
        return null
    }

    private fun PsiImportStaticStatement.isUsedStarImportOfEnumStaticFunctions(): Boolean {
        val importedEnumQualifiedName = importReference?.qualifiedName ?: return false
        if ((resolveTargetClass() as? KtLightClass)?.isEnum != true) return false

        fun PsiReference.isQualifiedNameInEnumStaticMethods(): Boolean {
            val referenceExpression = resolve() as? PsiMember ?: return false
            return referenceExpression.containingClass?.kotlinFqName == FqName(importedEnumQualifiedName)
                    && referenceExpression.name in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES_IN_JAVA.map { it.asString() }
        }

        return containingFile.anyDescendantOfType(PsiReferenceExpression::isQualifiedNameInEnumStaticMethods)
    }

    context(KaSession)
    private fun KtImportDirective.resolveReferenceToSymbol(): KaSymbol? = when (importedReference) {
        is KtReferenceExpression -> importedReference as KtReferenceExpression
        else -> importedReference?.getChildOfType<KtReferenceExpression>()
    }?.mainReference?.resolveToSymbol()

    context(KaSession)
    private fun KtImportDirective.isUsedStarImportOfEnumStaticFunctions(): Boolean {
        if (importPath?.isAllUnder != true) return false
        val importedEnumFqName = this.importedFqName ?: return false
        val importedClass = resolveReferenceToSymbol() as? KaClassSymbol ?: return false
        if (importedClass.classKind != KaClassKind.ENUM_CLASS) return false

        val enumStaticMethods = ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES.map { FqName("$importedEnumFqName.$it") }

        fun KtExpression.isNameInEnumStaticMethods(): Boolean {
            if (getQualifiedExpressionForSelector() != null) return false
            if (((this as? KtNameReferenceExpression)?.parent as? KtCallableReferenceExpression)?.receiverExpression != null) return false
            val symbol = mainReference?.resolveToSymbol() as? KaCallableSymbol ?: return false
            return symbol.callableId?.asSingleFqName() in enumStaticMethods
        }

        return containingFile.anyDescendantOfType<KtExpression> {
            (it as? KtCallExpression)?.isNameInEnumStaticMethods() == true
                    || (it as? KtCallableReferenceExpression)?.callableReference?.isNameInEnumStaticMethods() == true
                    || (it as? KtReferenceExpression)?.isNameInEnumStaticMethods() == true
        }
    }

    /**
     * Check static java imports according to the following pattern: 'org.test.Enum.(values/valueOf)'
     */
    private fun PsiImportStaticReferenceElement.isReferenceToBuiltInEnumFunction(): Boolean {
        val importedEnumQualifiedName = classReference.qualifiedName
        val enumStaticMethods = ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES_IN_JAVA.map { FqName("$importedEnumQualifiedName.$it") }
        return FqName(qualifiedName) in enumStaticMethods
    }

    private fun PsiReferenceExpression.isReferenceToBuiltInEnumFunction(enumClass: KtClass): Boolean {
        val reference = resolve() as? KtLightMethod ?: return false
        return reference.containingClass.name == enumClass.name && reference is SyntheticElement && reference.name in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES_IN_JAVA.map { it.asString() }
    }

    context(KaSession)
    private fun KtCallableDeclaration.canBeHandledByLightMethods(symbol: KaDeclarationSymbol?): Boolean {
        return when {
            symbol is KaConstructorSymbol -> {
                val classSymbol = symbol.containingDeclaration as? KaNamedClassSymbol ?: return false
                !classSymbol.isInline && classSymbol.visibility != KaSymbolVisibility.PRIVATE
            }
            hasModifier(KtTokens.INTERNAL_KEYWORD) -> false
            symbol !is KaNamedFunctionSymbol -> true
            else -> !symbol.hasInlineClassParameters()
        }
    }

    context(KaSession)
    private fun KaNamedFunctionSymbol.hasInlineClassParameters(): Boolean {
        val receiverParameterClassSymbol = receiverType?.expandedSymbol as? KaNamedClassSymbol
        return receiverParameterClassSymbol?.isInline == true || valueParameters.any {
            val namedClassOrObjectSymbol = it.returnType.expandedSymbol as? KaNamedClassSymbol ?: return@any false
            namedClassOrObjectSymbol.isInline
        }
    }

    private fun hasOverrides(declaration: KtNamedDeclaration): Boolean {
        // don't search for functional expressions to check if function is used
        val overrides = when (declaration) {
            is KtCallableDeclaration -> declaration.hasAnyOverridings()
            is KtClass -> declaration.hasAnyInheritors()
            else -> false
        }

        return overrides
    }

    private fun hasFakeOverrides(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean {
        val ownerClass = declaration.containingClassOrObject as? KtClass ?: return false
        if (!ownerClass.isInheritable()) return false
        val callableName = analyze(declaration) {
            val symbol = declaration.symbol
            if (symbol !is KaCallableSymbol) return false
            val modality = symbol.modality
            if (modality == KaSymbolModality.ABSTRACT) return false
            symbol.callableId?.callableName
        } ?: return false

        return ownerClass.findAllInheritors(useScope).any { element: PsiElement ->
            when (element) {
                is KtClassOrObject -> {
                    analyze(element) {
                        if (!element.canBeAnalysed()) return@any false

                        val callableSymbol = declaration.symbol as KaCallableSymbol
                        val overridingCallableSymbol = element.classSymbol
                            ?.memberScope
                            ?.callables(callableName)
                            ?.singleOrNull {
                                it.fakeOverrideOriginal == callableSymbol
                            }
                            ?: return@any false

                        overridingCallableSymbol != callableSymbol && overridingCallableSymbol.intersectionOverriddenSymbols.any { it != callableSymbol }
                    }
                }
                is PsiClass ->
                    declaration.toLightMethods().any { lightMethod ->
                        val sameMethods = element.findMethodsBySignature(lightMethod, true)
                        sameMethods.all { it.containingClass != element } &&
                                sameMethods.any { it.containingClass != lightMethod.containingClass }
                    }
                else ->
                    false
            }
        }
    }

    private fun hasPlatformImplementations(declaration: KtNamedDeclaration): Boolean {
        return declaration.hasExpectModifier()

        // TODO: K2 counterpart of `hasActualsFor` is missing. Update this function after implementing it.
    }

    private fun classOrObjectHasTextUsages(classOrObject: KtClassOrObject): Boolean {
        var hasTextUsages = false

        // Finding text usages
        if (classOrObject.useScope is GlobalSearchScope) {
            val findClassUsagesHandler = KotlinFindClassUsagesHandler(classOrObject, KotlinFindUsagesHandlerFactory(classOrObject.project))
            findClassUsagesHandler.processUsagesInText(
                classOrObject,
                { hasTextUsages = true; false },
                GlobalSearchScope.projectScope(classOrObject.project)
            )
        }

        return hasTextUsages
    }

    fun createQuickFixes(declaration: KtNamedDeclaration): Array<LocalQuickFixAndIntentionActionOnPsiElement> {
        if (declaration is KtParameter) {
            if (declaration.isLoopParameter) {
                return emptyArray()
            }
            if (declaration.isCatchParameter) {
                return if (declaration.name == "_") {
                    emptyArray()
                } else {
                    arrayOf(RenameElementFix(declaration, "_"))
                }
            }
            val ownerFunction = declaration.ownerFunction
            if (ownerFunction is KtPropertyAccessor && ownerFunction.isSetter) {
                return emptyArray()
            }
            if (ownerFunction is KtFunctionLiteral) {
                return arrayOf(RenameElementFix(declaration, "_"))
            }
        }
        // TODO: Implement K2 counterpart of `createAddToDependencyInjectionAnnotationsFix` and use it for `element` with annotations here.
        return arrayOf(SafeDeleteFix(declaration))
    }

    context(KaSession)
    private fun isEntryPoint(
        declaration: KtNamedDeclaration,
        isCheapEnough: Lazy<PsiSearchHelper.SearchCostResult>,
        isJavaEntryPoint: UnusedDeclarationInspectionBase
    ): Boolean {
        if (declaration.hasKotlinAdditionalAnnotation()) return true
        val lightElement: PsiElement = when (declaration) {
            is KtClass -> {
                if (declaration.declarations.any { it.hasKotlinAdditionalAnnotation() }) return true
                declaration.toLightClass()
            }
            is KtObjectDeclaration -> declaration.toLightClass()
            is KtNamedFunction -> {
                // Some of the main-function-cases are covered by 'javaInspection.isEntryPoint(lightElement)' call
                // but not all of them: light method for parameterless main still points to parameterless name
                // that is not an actual entry point from Java language point of view
                // TODO: If we would add options for this inspection, then this call should be conditional.
                if (KotlinMainFunctionDetector.getInstance().isMain(declaration)) return true
                LightClassUtil.getLightClassMethod(declaration as KtFunction)
            }
            is KtSecondaryConstructor -> LightClassUtil.getLightClassMethod(declaration as KtFunction)
            is KtProperty, is KtParameter -> {
                if (declaration is KtParameter) {
                    val ownerFunction = declaration.ownerFunction
                    if (ownerFunction is KtNamedFunction && KotlinMainFunctionDetector.getInstance().isMain(ownerFunction)) {
                        // @JvmStatic main() must have parameters
                        return ownerFunction.findAnnotation(JvmStandardClassIds.Annotations.JvmStatic) != null
                    }
                    if (!declaration.hasValOrVar()) return false
                }
                // we may handle only annotation parameters so far
                if (declaration is KtParameter && isAnnotationParameter(declaration)) {
                    val lightAnnotationMethods = LightClassUtil.getLightClassPropertyMethods(declaration).toList()
                    for (javaParameterPsi in lightAnnotationMethods) {
                        if (isJavaEntryPoint.isEntryPoint(javaParameterPsi)) {
                            return true
                        }
                    }
                }
                // can't rely on a light element, check annotation ourselves
                val entryPointsManager = EntryPointsManager.getInstance(declaration.project) as EntryPointsManagerBase
                return checkAnnotatedUsingPatterns(
                    declaration,
                    entryPointsManager.additionalAnnotations + entryPointsManager.ADDITIONAL_ANNOTATIONS
                )
            }
            else -> return false
        } ?: return false

        if (isCheapEnough.value == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return false

        return isJavaEntryPoint.isEntryPoint(lightElement)
    }
}