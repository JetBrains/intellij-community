// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.symbols.markers.isPrivateOrPrivateToThis
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.codeinsight.api.inspections.KotlinSingleElementInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.idea.inspections.describe
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.findScriptsWithUsages
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.searching.inheritors.findAllInheritors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

/**
 * Current inspection does nothing
 */
internal class UnusedSymbolInspection : KotlinSingleElementInspection<KtNamedDeclaration>(KtNamedDeclaration::class), UnfairLocalInspectionTool {

    // TODO: Having parity between Java and Kotlin might be a good idea once we replace the global Kotlin inspection with a UAST-based one.
    private val javaInspection = UnusedDeclarationInspection()

    private val KOTLIN_ADDITIONAL_ANNOTATIONS = listOf("kotlin.test.*", "kotlin.js.JsExport")

    private fun isApplicableByPsi(declaration: KtNamedDeclaration): Boolean {
        if (!RootKindFilter.projectSources.matches(declaration)) return false

        // Simple PSI-based checks
        if (declaration is KtObjectDeclaration && declaration.isCompanion()) return false // never mark companion object as unused (there are too many reasons it can be needed for)

        if (declaration is KtSecondaryConstructor && declaration.containingClass()?.isEnum() == true) return false
        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        if (declaration is KtProperty && declaration.isLocal) return false
        return declaration !is KtParameter || (declaration.getParent().parent is KtPrimaryConstructor && declaration.hasValOrVar())
    }

    context(KtAnalysisSession)
    private fun getPsiToReportProblem(declaration: KtNamedDeclaration): PsiElement? {
        val symbol = declaration.getSymbol()
        if (declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED && (symbol as? KtSymbolWithVisibility)?.visibility?.isPublicAPI == true) {
            return null
        }
        if (symbol is KtFunctionSymbol && symbol.isOperator) return null

        val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) {
            isCheapEnoughToSearchUsages(declaration)
        }
        if (isEntryPoint(declaration, isCheapEnough)) return null
        if (declaration.isFinalizeMethod()) return null
        if (declaration is KtProperty && declaration.isSerializationImplicitlyUsedField()) return null
        if (declaration is KtNamedFunction && declaration.isSerializationImplicitlyUsedMethod()) return null
        // properties can be referred by component1/component2, which is too expensive to search, don't mark them as unused
        if (declaration.isConstructorDeclaredProperty() &&
            declaration.containingClass()?.mustHaveNonEmptyPrimaryConstructor() == true
        ) return null
        // experimental annotations
        if (symbol is KtClassOrObjectSymbol && symbol.classKind == KtClassKind.ANNOTATION_CLASS) {
            val fqName = symbol.nameOrAnonymous.asString()
            val languageVersionSettings = declaration.languageVersionSettings
            if (fqName in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return null
        }

        // Main checks: finding reference usages && text usages
        if (hasNonTrivialUsages(declaration, isCheapEnough, symbol)) return null
        if (declaration is KtClassOrObject && classOrObjectHasTextUsages(declaration)) return null

        return declaration.nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword()
    }

    context(KtAnalysisSession)
    private fun KtDeclaration.hasKotlinAdditionalAnnotation() =
        this is KtNamedDeclaration && checkAnnotatedUsingPatterns(this, KOTLIN_ADDITIONAL_ANNOTATIONS)

    private fun KtProperty.isSerializationImplicitlyUsedField(): Boolean {
        val ownerObject = getNonStrictParentOfType<KtClassOrObject>() as? KtObjectDeclaration ?: return false
        val lightClass = if (ownerObject.isCompanion()) {
            ownerObject.getNonStrictParentOfType<KtClass>()?.toLightClass()
        } else {
            ownerObject.toLightClass()
        } ?: return false
        return lightClass.fields.any { it.name == name && HighlightUtil.isSerializationImplicitlyUsedField(it) }
    }

    private fun KtNamedFunction.isSerializationImplicitlyUsedMethod(): Boolean =
        toLightMethods().any { JavaHighlightUtil.isSerializationRelatedMethod(it, it.containingClass) }


    fun PsiNamedElement.getClassNameForCompanionObject(): String? {
        return if (this is KtObjectDeclaration && this.isCompanion()) {
            getNonStrictParentOfType<KtClass>()?.name
        } else {
            null
        }
    }

    private fun isCheapEnoughToSearchUsages(declaration: KtNamedDeclaration): SearchCostResult {
        val project = declaration.project
        val psiSearchHelper = PsiSearchHelper.getInstance(project)

        if (!findScriptsWithUsages(declaration) { DefaultScriptingSupport.getInstance(project).isLoadedFromCache(it) }) {
            // Not all script configuration are loaded; behave like it is used
            return TOO_MANY_OCCURRENCES
        }

        val useScope = psiSearchHelper.getUseScope(declaration)
        if (useScope is GlobalSearchScope) {
            var zeroOccurrences = true
            val list = listOf(declaration.name) + declarationAccessorNames(declaration) +
                    listOfNotNull(declaration.getClassNameForCompanionObject())
            for (name in list) {
                if (name == null) continue
                when (psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope, null, null)) {
                    ZERO_OCCURRENCES -> {
                    } // go on, check other names
                    FEW_OCCURRENCES -> zeroOccurrences = false
                    TOO_MANY_OCCURRENCES -> return TOO_MANY_OCCURRENCES // searching usages is too expensive; behave like it is used
                }
            }

            if (zeroOccurrences) return ZERO_OCCURRENCES
        }
        return FEW_OCCURRENCES
    }

    /**
     * returns list of declaration accessor names e.g. pair of getter/setter for property declaration
     *
     * note: could be more than declaration.getAccessorNames()
     * as declaration.getAccessorNames() relies on LightClasses and therefore some of them could be not available
     * (as not accessible outside of class)
     *
     * e.g.: private setter w/o body is not visible outside of class and could not be used
     */
    private fun declarationAccessorNames(declaration: KtNamedDeclaration): List<String> =
        when (declaration) {
            is KtProperty -> listOfPropertyAccessorNames(declaration)
            is KtParameter -> listOfParameterAccessorNames(declaration)
            else -> emptyList()
        }

    private fun listOfParameterAccessorNames(parameter: KtParameter): List<String> {
        val accessors = mutableListOf<String>()
        if (parameter.hasValOrVar()) {
            parameter.name?.let {
                accessors.add(JvmAbi.getterName(it))
                if (parameter.isVarArg)
                    accessors.add(JvmAbi.setterName(it))
            }
        }
        return accessors
    }

    private fun listOfPropertyAccessorNames(property: KtProperty): List<String> {
        val accessors = mutableListOf<String>()
        val propertyName = property.name ?: return accessors
        accessors.add(property.getCustomGetterName() ?: JvmAbi.getterName(propertyName))
        if (property.isVar) accessors.add(property.getCustomSetterName() ?: JvmAbi.setterName(propertyName))
        return accessors
    }

    private fun KtProperty.getCustomGetterName(): String? = getter?.annotationEntries?.getCustomAccessorName()
        ?: annotationEntries.filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_GETTER }.getCustomAccessorName()

    private fun KtProperty.getCustomSetterName(): String? = setter?.annotationEntries?.getCustomAccessorName()
        ?: annotationEntries.filter { it.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_SETTER }.getCustomAccessorName()

    // If the property or its accessor has 'JvmName' annotation it should be used instead
    private fun List<KtAnnotationEntry>.getCustomAccessorName(): String? {
        val customJvmNameAnnotation = firstOrNull { it.shortName?.asString() == "JvmName" } ?: return null
        return customJvmNameAnnotation.valueArguments.firstOrNull()?.getArgumentExpression()?.let { ElementManipulators.getValueText(it) }
    }

    context(KtAnalysisSession)
    private fun isEntryPoint(declaration: KtNamedDeclaration, isCheapEnough: Lazy<SearchCostResult>): Boolean {
        if (declaration.hasKotlinAdditionalAnnotation()) return true
        if (declaration is KtClass && declaration.declarations.any { it.hasKotlinAdditionalAnnotation() }) return true

        // Some of the main-function-cases are covered by 'javaInspection.isEntryPoint(lightElement)' call
        // but not all of them: light method for parameterless main still points to parameterless name
        // that is not an actual entry point from Java language point of view
        // TODO: If we would add options for this inspection, then this call should be conditional.
        if (declaration is KtNamedFunction && KotlinMainFunctionDetector.getInstance().isMain(declaration)) return true

        val lightElement: PsiElement = when (declaration) {
            is KtClassOrObject -> declaration.toLightClass()
            is KtNamedFunction, is KtSecondaryConstructor -> LightClassUtil.getLightClassMethod(declaration as KtFunction)
            is KtProperty, is KtParameter -> {
                if (declaration is KtParameter && !declaration.hasValOrVar()) return false
                // we may handle only annotation parameters so far
                if (declaration is KtParameter && isAnnotationParameter(declaration)) {
                    val lightAnnotationMethods = LightClassUtil.getLightClassPropertyMethods(declaration).toList()
                    for (javaParameterPsi in lightAnnotationMethods) {
                        if (javaInspection.isEntryPoint(javaParameterPsi)) {
                            return true
                        }
                    }
                }
                // can't rely on light element, check annotation ourselves
                val entryPointsManager = EntryPointsManager.getInstance(declaration.project) as EntryPointsManagerBase
                return checkAnnotatedUsingPatterns(
                    declaration,
                    entryPointsManager.additionalAnnotations + entryPointsManager.ADDITIONAL_ANNOTATIONS
                )
            }
            else -> return false
        } ?: return false

        if (isCheapEnough.value == TOO_MANY_OCCURRENCES) return false

        return javaInspection.isEntryPoint(lightElement)
    }

    private fun isAnnotationParameter(parameter: KtParameter): Boolean {
        val constructor = parameter.ownerFunction as? KtConstructor<*> ?: return false
        return constructor.containingClassOrObject?.isAnnotation() ?: false
    }

    // variation of IDEA's AnnotationUtil.checkAnnotatedUsingPatterns()
    context(KtAnalysisSession)
    private fun checkAnnotatedUsingPatterns(
        declaration: KtNamedDeclaration,
        annotationPatterns: Collection<String>
    ): Boolean {
        if (declaration.annotationEntries.isEmpty()) return false
        val annotationsPresent = declaration.annotationEntries.mapNotNull {
            val reference = it?.calleeExpression?.constructorReferenceExpression?.mainReference ?: return@mapNotNull null
            val symbol = reference.resolveToSymbol() ?: return@mapNotNull null
            val constructorSymbol = symbol as? KtConstructorSymbol ?: return@mapNotNull null
            constructorSymbol.containingClassIdIfNonLocal?.asSingleFqName()?.asString()
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

    context(KtAnalysisSession)
    private fun checkDeclaration(declaration: KtNamedDeclaration, importedDeclaration: KtNamedDeclaration): Boolean =
        declaration !in importedDeclaration.parentsWithSelf && !hasNonTrivialUsages(importedDeclaration)

    context(KtAnalysisSession)
    private fun hasNonTrivialUsages(declaration: KtNamedDeclaration, symbol: KtDeclarationSymbol? = null): Boolean {
        val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) { isCheapEnoughToSearchUsages(declaration) }
        return hasNonTrivialUsages(declaration, isCheapEnough, symbol)
    }

    context(KtAnalysisSession)
    private fun hasNonTrivialUsages(
        declaration: KtNamedDeclaration,
        enoughToSearchUsages: Lazy<SearchCostResult>,
        symbol: KtDeclarationSymbol? = null
    ): Boolean {
        val project = declaration.project
        val psiSearchHelper = PsiSearchHelper.getInstance(project)

        val useScope = psiSearchHelper.getUseScope(declaration)
        val restrictedScope = if (useScope is GlobalSearchScope) {
            val zeroOccurrences = when (enoughToSearchUsages.value) {
                ZERO_OCCURRENCES -> true
                FEW_OCCURRENCES -> false
                TOO_MANY_OCCURRENCES -> return true // searching usages is too expensive; behave like it is used
            }

            if (zeroOccurrences && !declaration.hasActualModifier()) {
                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                    // go on: companion object can be used only in containing class
                } else {
                    return false
                }
            }
            if (declaration.hasActualModifier()) {
                KotlinSourceFilterScope.projectSources(project.projectScope(), project)
            } else {
                KotlinSourceFilterScope.projectSources(useScope, project)
            }
        } else useScope

        if (declaration is KtTypeParameter) {
            val containingClass = declaration.containingClass()
            if (containingClass != null) {
                val isOpenClass = containingClass.isInterface()
                        || containingClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                        || containingClass.hasModifier(KtTokens.SEALED_KEYWORD)
                        || containingClass.hasModifier(KtTokens.OPEN_KEYWORD)
                if (isOpenClass && hasOverrides(containingClass, restrictedScope)) return true

                val containingClassSearchScope = GlobalSearchScope.projectScope(project)
                val isRequiredToCallFunction =
                    ReferencesSearch.search(KotlinReferencesSearchParameters(containingClass, containingClassSearchScope)).any { ref ->
                        val userType = ref.element.parent as? KtUserType ?: return@any false
                        val typeArguments = userType.typeArguments
                        if (typeArguments.isEmpty()) return@any false

                        val parameter = userType.getStrictParentOfType<KtParameter>() ?: return@any false
                        val callableDeclaration = parameter.getStrictParentOfType<KtCallableDeclaration>()?.let {
                            if (it !is KtNamedFunction) it.containingClass() else it
                        } ?: return@any false
                        val typeParameters = callableDeclaration.typeParameters.map { it.name }
                        if (typeParameters.isEmpty()) return@any false
                        if (typeArguments.none { it.text in typeParameters }) return@any false

                        ReferencesSearch.search(KotlinReferencesSearchParameters(callableDeclaration, containingClassSearchScope)).any {
                            val callElement = it.element.parent as? KtCallElement
                            callElement != null && callElement.typeArgumentList == null
                        }
                    }
                if (isRequiredToCallFunction) return true
            }
        }

        return (declaration is KtObjectDeclaration && declaration.isCompanion() &&
                declaration.body?.declarations?.isNotEmpty() == true) ||
                hasReferences(declaration, symbol, restrictedScope) ||
                hasOverrides(declaration, restrictedScope) ||
                hasFakeOverrides(declaration, restrictedScope, symbol) ||
                hasPlatformImplementations(declaration, symbol)
    }

    private val KtNamedDeclaration.isObjectOrEnum: Boolean get() = this is KtObjectDeclaration || this is KtClass && isEnum()

    context(KtAnalysisSession)
    private fun checkReference(ref: PsiReference, declaration: KtNamedDeclaration, symbol: KtDeclarationSymbol?): Boolean {
        if (declaration.isAncestor(ref.element)) return true // usages inside element's declaration are not counted

        if (ref.element.parent is KtValueArgumentName) return true // usage of parameter in form of named argument is not counted

        val import = ref.element.getParentOfType<KtImportDirective>(false)
        if (import != null) {
            if (import.aliasName != null && import.aliasName != declaration.name) {
                return false
            }
            // check if we import member(s) from object / nested object / enum and search for their usages
            val originalDeclaration = (symbol as? KtTypeAliasSymbol)?.expandedType?.expandedClassSymbol?.psi as? KtNamedDeclaration
            if (declaration is KtClassOrObject || originalDeclaration is KtClassOrObject) {
                if (import.isAllUnder) {
                    val importedFrom = import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve()
                            as? KtClassOrObject ?: return true
                    return importedFrom.declarations.none { it is KtNamedDeclaration && hasNonTrivialUsages(it) }
                } else {
                    if (import.importedFqName != declaration.fqName) {
                        val importedDeclaration =
                            import.importedReference?.getQualifiedElementSelector()?.mainReference?.resolve() as? KtNamedDeclaration
                                ?: return true

                        if (declaration.isObjectOrEnum || importedDeclaration.containingClassOrObject is KtObjectDeclaration) return checkDeclaration(
                            declaration,
                            importedDeclaration
                        )

                        if (originalDeclaration?.isObjectOrEnum == true) return checkDeclaration(
                            originalDeclaration,
                            importedDeclaration
                        )

                        // check type alias
                        if (importedDeclaration.fqName == declaration.fqName) return true
                    }
                }
            }
            return true
        }

        return false
    }

    context(KtAnalysisSession)
    private fun hasReferences(
        declaration: KtNamedDeclaration,
        symbol: KtDeclarationSymbol?,
        useScope: SearchScope
    ): Boolean {
        fun checkReference(ref: PsiReference): Boolean = checkReference(ref, declaration, symbol)

        val searchOptions = KotlinReferencesSearchOptions(acceptCallableOverrides = declaration.hasActualModifier())
        val searchParameters = KotlinReferencesSearchParameters(
            declaration,
            useScope,
            kotlinOptions = searchOptions
        )
        val referenceUsed: Boolean by lazy { !ReferencesSearch.search(searchParameters).forEach(Processor { checkReference(it) }) }

        if (symbol is KtFunctionSymbol && symbol.annotationsList.hasAnnotation(ClassId.topLevel(FqName("kotlin.jvm.JvmName")))) {
            if (referenceUsed) return true
        }

        if (declaration is KtSecondaryConstructor) {
            val containingClass = declaration.containingClass()
            if (containingClass != null && ReferencesSearch.search(KotlinReferencesSearchParameters(containingClass, useScope)).any {
                    it.element.getStrictParentOfType<KtTypeAlias>() != null || it.element.getStrictParentOfType<KtCallExpression>()
                        ?.resolveCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol == symbol
                }) return true
        }

        if (declaration is KtCallableDeclaration && declaration.canBeHandledByLightMethods(symbol)) {
            val lightMethods = declaration.toLightMethods()
            if (lightMethods.isNotEmpty()) {
                val lightMethodsUsed = lightMethods.any { method ->
                    !MethodReferencesSearch.search(method).forEach(Processor { checkReference(it) })
                }
                if (lightMethodsUsed) return true
                if (!declaration.hasActualModifier()) return false
            }
        }

        if (declaration is KtEnumEntry) {
            val enumClass = declaration.containingClass()?.takeIf { it.isEnum() }
            if (hasBuiltInEnumFunctionReference(enumClass, useScope)) return true
        }

        return referenceUsed || checkPrivateDeclaration(declaration, symbol)
    }

    /**
     * Return true if [declaration] is a private nested class or object that is referenced by an import directive and the target symbol of
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
    context(KtAnalysisSession)
    private fun checkPrivateDeclaration(declaration: KtNamedDeclaration, symbol: KtDeclarationSymbol?): Boolean {
        if (symbol == null || !declaration.isPrivateNestedClassOrObject) return false

        val setOfImportedDeclarations = hashSetOf<KtSimpleNameExpression>()
        declaration.containingKtFile.importList?.acceptChildren(simpleNameExpressionRecursiveVisitor {
            setOfImportedDeclarations += it
        })

        return setOfImportedDeclarations.mapNotNull { it.referenceExpression() }
            .filter { symbol in it.mainReference.resolveToSymbols() }
            .any { !checkReference(it.mainReference, declaration, symbol) }
    }

    context(KtAnalysisSession)
    private fun hasBuiltInEnumFunctionReference(enumClass: KtClass?, useScope: SearchScope): Boolean {
        if (enumClass == null) return false
        val isFoundEnumFunctionReferenceViaSearch = ReferencesSearch.search(KotlinReferencesSearchParameters(enumClass, useScope))
            .any { hasBuiltInEnumFunctionReference(it, enumClass) }

        return isFoundEnumFunctionReferenceViaSearch || hasEnumFunctionReferenceInEnumClass(enumClass)
    }

    context(KtAnalysisSession)
    private fun KtSimpleNameExpression.isReferenceToBuiltInEnumEntries(): Boolean =
        isEnumValuesSoftDeprecateEnabled() && this.getReferencedNameAsName() == StandardNames.ENUM_ENTRIES && isSynthesizedFunction()

    /**
     * Checks calls in enum class without receiver expression. Example: values(), ::values
     */
    context(KtAnalysisSession)
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
    context(KtAnalysisSession)
    private fun hasBuiltInEnumFunctionReference(reference: PsiReference, enumClass: KtClass): Boolean {
        val parent = reference.element.parent
        if ((parent as? KtQualifiedExpression)?.normalizeEnumQualifiedExpression(enumClass)?.canBeReferenceToBuiltInEnumFunction() == true) return true
        if ((parent as? KtQualifiedExpression)?.normalizeEnumCallableReferenceExpression(enumClass)?.canBeReferenceToBuiltInEnumFunction() == true) return true
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
        if (this.parent !is KtQualifiedExpression && this.receiverExpression.text == enumClass.name) return this
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

    context(KtAnalysisSession)
    private fun KtImportDirective.resolveReferenceToSymbol(): KtSymbol? = when(importedReference) {
        is KtReferenceExpression -> importedReference as KtReferenceExpression
        else -> importedReference?.getChildOfType<KtReferenceExpression>()
    }?.mainReference?.resolveToSymbol()

    context(KtAnalysisSession)
    private fun KtImportDirective.isUsedStarImportOfEnumStaticFunctions(): Boolean {
        if (importPath?.isAllUnder != true) return false
        val importedEnumFqName = this.importedFqName ?: return false
        val importedClass = resolveReferenceToSymbol() as? KtClassOrObjectSymbol ?: return false
        if (importedClass.classKind != KtClassKind.ENUM_CLASS) return false

        val enumStaticMethods = ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES.map { FqName("$importedEnumFqName.$it") }

        fun KtExpression.isNameInEnumStaticMethods(): Boolean {
            if (getQualifiedExpressionForSelector() != null) return false
            if (((this as? KtNameReferenceExpression)?.parent as? KtCallableReferenceExpression)?.receiverExpression != null) return false
            val symbol = mainReference?.resolveToSymbol() as? KtCallableSymbol ?: return false
            return symbol.callableIdIfNonLocal?.asSingleFqName() in enumStaticMethods
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

    context(KtAnalysisSession)
    private fun KtCallableDeclaration.canBeHandledByLightMethods(symbol: KtDeclarationSymbol?): Boolean {
        return when {
            symbol is KtConstructorSymbol -> {
                val classSymbol = symbol.getContainingSymbol() as? KtNamedClassOrObjectSymbol ?: return false
                !classSymbol.isInline && !classSymbol.visibility.isPrivateOrPrivateToThis()
            }
            hasModifier(KtTokens.INTERNAL_KEYWORD) -> false
            symbol !is KtFunctionSymbol -> true
            else -> !symbol.hasInlineClassParameters()
        }
    }

    context(KtAnalysisSession)
    private fun KtFunctionSymbol.hasInlineClassParameters(): Boolean {
        val receiverParameterClassSymbol = receiverType?.expandedClassSymbol as? KtNamedClassOrObjectSymbol
        return receiverParameterClassSymbol?.isInline == true || valueParameters.any {
            val namedClassOrObjectSymbol = it.returnType.expandedClassSymbol as? KtNamedClassOrObjectSymbol ?: return@any false
            namedClassOrObjectSymbol.isInline
        }
    }

    private fun hasOverrides(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean =
        DefinitionsScopedSearch.search(declaration, useScope).findFirst() != null

    context(KtAnalysisSession)
    private fun hasFakeOverrides(declaration: KtNamedDeclaration, useScope: SearchScope, symbol: KtDeclarationSymbol?): Boolean {
        val ownerClass = declaration.containingClassOrObject as? KtClass ?: return false
        if (!ownerClass.isInheritable()) return false
        val callableSymbol = symbol as? KtCallableSymbol ?: return false
        if ((callableSymbol as? KtSymbolWithModality)?.modality == Modality.ABSTRACT) return false
        return ownerClass.findAllInheritors(useScope).any { element: PsiElement ->
            when (element) {
                is KtClassOrObject -> {
                    val overridingCallableSymbol = element.getClassOrObjectSymbol()?.getMemberScope()
                        ?.getCallableSymbols { name -> name == callableSymbol.callableIdIfNonLocal?.callableName }?.filter {
                            it.unwrapFakeOverrides == callableSymbol
                        }?.singleOrNull() ?: return@any false
                    overridingCallableSymbol != callableSymbol && overridingCallableSymbol.getIntersectionOverriddenSymbols()
                        .any { it != callableSymbol }
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

    context(KtAnalysisSession)
    private fun hasPlatformImplementations(declaration: KtNamedDeclaration, symbol: KtDeclarationSymbol?): Boolean {
        if (!declaration.hasExpectModifier()) return false

        // TODO: K2 counterpart of `hasActualsFor` is missing. Update this function after implementing it.
        return true
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

    private fun createQuickFixes(element: KtNamedDeclaration): List<LocalQuickFix> {
        // TODO: Implement K2 counterpart of `createAddToDependencyInjectionAnnotationsFix` and use it for `element` with annotations here.
        return listOf(SafeDeleteFix(element))
    }

    override fun visitTargetElement(element: KtNamedDeclaration, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (isOnTheFly) return
        val message = element.describe()?.let { KotlinBaseHighlightingBundle.message("inspection.message.never.used", it) } ?: return
        if (!isApplicableByPsi(element)) return
        val psiToReportProblem = analyze(element) { getPsiToReportProblem(element) } ?: return
        holder.registerProblem(psiToReportProblem, message, *createQuickFixes(element).toTypedArray())
    }
}