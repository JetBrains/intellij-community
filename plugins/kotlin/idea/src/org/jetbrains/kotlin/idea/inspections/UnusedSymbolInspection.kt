// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.options.JavaInspectionButtons
import com.intellij.codeInsight.options.JavaInspectionControls
import com.intellij.codeInspection.*
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import com.intellij.util.Processor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtUltraLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.mustHaveNonEmptyPrimaryConstructor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.base.searching.usages.handlers.KotlinFindClassUsagesHandler
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.caches.project.implementingDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.completion.KotlinIdeaCompletionBundle
import org.jetbrains.kotlin.idea.core.isInheritable
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.intentions.isFinalizeMethod
import org.jetbrains.kotlin.idea.intentions.isReferenceToBuiltInEnumEntries
import org.jetbrains.kotlin.idea.intentions.isReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.intentions.isUsedStarImportOfEnumStaticFunctions
import org.jetbrains.kotlin.idea.isMainFunction
import org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.search.findScriptsWithUsages
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.search.usagesSearch.getClassNameForCompanionObject
import org.jetbrains.kotlin.idea.util.hasActualsFor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.checkers.explicitApiEnabled
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyPublicApi
import org.jetbrains.kotlin.resolve.isInlineClass
import org.jetbrains.kotlin.resolve.isInlineClassType
import org.jetbrains.kotlin.util.findCallableMemberBySignature

class UnusedSymbolInspection : AbstractKotlinInspection() {
    companion object {
        private val javaInspection = UnusedDeclarationInspection()

        private val KOTLIN_ADDITIONAL_ANNOTATIONS = listOf("kotlin.test.*", "kotlin.js.JsExport")

        private fun KtDeclaration.hasKotlinAdditionalAnnotation() =
            this is KtNamedDeclaration && checkAnnotatedUsingPatterns(this, KOTLIN_ADDITIONAL_ANNOTATIONS)

        fun isEntryPoint(declaration: KtNamedDeclaration): Boolean =
            isEntryPoint(declaration, lazy(LazyThreadSafetyMode.NONE) { isCheapEnoughToSearchUsages(declaration) })

        private fun isEntryPoint(declaration: KtNamedDeclaration, isCheapEnough: Lazy<SearchCostResult>): Boolean {
            if (declaration.hasKotlinAdditionalAnnotation()) return true
            if (declaration is KtClass && declaration.declarations.any { it.hasKotlinAdditionalAnnotation() }) return true

            // Some of the main-function-cases are covered by 'javaInspection.isEntryPoint(lightElement)' call
            // but not all of them: light method for parameterless main still points to parameterless name
            // that is not an actual entry point from Java language point of view
            if (declaration.isMainFunction()) return true

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
            accessors.add(property.getter?.let { getCustomAccessorName(it) } ?: JvmAbi.getterName(propertyName))
            if (property.isVar)
                accessors.add(property.setter?.let { getCustomAccessorName(it) } ?: JvmAbi.setterName(propertyName))
            return accessors
        }

        /*
            If the property has 'JvmName' annotation at accessor it should be used instead
         */
        private fun getCustomAccessorName(method: KtPropertyAccessor?): String? {
            val customJvmNameAnnotation =
                method?.annotationEntries?.firstOrNull { it.shortName?.asString() == "JvmName" } ?: return null
            return customJvmNameAnnotation.findDescendantOfType<KtStringTemplateEntry>()?.text
        }

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

        // variation of IDEA's AnnotationUtil.checkAnnotatedUsingPatterns()
        fun checkAnnotatedUsingPatterns(
            declaration: KtNamedDeclaration,
            annotationPatterns: Collection<String>
        ): Boolean {
            if (declaration.annotationEntries.isEmpty()) return false
            val context = declaration.analyze()
            val annotationsPresent = declaration.annotationEntries.mapNotNull {
                context[BindingContext.ANNOTATION, it]?.fqName?.asString()
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
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return namedDeclarationVisitor(fun(declaration) {
            ProgressManager.checkCanceled()
            val message = declaration.describe()?.let { KotlinIdeaCompletionBundle.message("inspection.message.never.used", it) } ?: return

            if (!RootKindFilter.projectSources.matches(declaration)) return

            // Simple PSI-based checks
            if (declaration is KtObjectDeclaration && declaration.isCompanion()) return // never mark companion object as unused (there are too many reasons it can be needed for)

            if (declaration is KtSecondaryConstructor && declaration.containingClass()?.isEnum() == true) return
            if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
            if (declaration is KtProperty && declaration.isLocal) return
            if (declaration is KtParameter &&
                (declaration.getParent().parent !is KtPrimaryConstructor || !declaration.hasValOrVar())
            ) return

            // More expensive, resolve-based checks
            val descriptor = declaration.resolveToDescriptorIfAny() ?: return
            if (declaration.languageVersionSettings.explicitApiEnabled
                && (descriptor as? DeclarationDescriptorWithVisibility)?.isEffectivelyPublicApi == true) {
                return
            }
            if (descriptor is FunctionDescriptor && descriptor.isOperator) return
            val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) {
                isCheapEnoughToSearchUsages(declaration)
            }
            if (isEntryPoint(declaration, isCheapEnough)) return
            if (declaration.isFinalizeMethod(descriptor)) return
            if (declaration is KtProperty && declaration.isSerializationImplicitlyUsedField()) return
            if (declaration is KtNamedFunction && declaration.isSerializationImplicitlyUsedMethod()) return
            // properties can be referred by component1/component2, which is too expensive to search, don't mark them as unused
            if (declaration.isConstructorDeclaredProperty() &&
                declaration.containingClass()?.mustHaveNonEmptyPrimaryConstructor() == true
            ) return
            // experimental annotations
            if (descriptor is ClassDescriptor && descriptor.kind == ClassKind.ANNOTATION_CLASS) {
                val fqName = descriptor.fqNameSafe.asString()
                val languageVersionSettings = declaration.languageVersionSettings
                if (fqName in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return
            }

            // Main checks: finding reference usages && text usages
            if (hasNonTrivialUsages(declaration, isCheapEnough, descriptor)) return
            if (declaration is KtClassOrObject && classOrObjectHasTextUsages(declaration)) return

            val psiElement = declaration.nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: return
            val problemDescriptor = holder.manager.createProblemDescriptor(
                psiElement,
                null,
                message,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                true,
                *createQuickFixes(declaration).toTypedArray()
            )

            holder.registerProblem(problemDescriptor)
        })
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

    private fun hasNonTrivialUsages(declaration: KtNamedDeclaration, descriptor: DeclarationDescriptor? = null): Boolean {
        val isCheapEnough = lazy(LazyThreadSafetyMode.NONE) { isCheapEnoughToSearchUsages(declaration) }
        return hasNonTrivialUsages(declaration, isCheapEnough, descriptor)
    }

    private fun hasNonTrivialUsages(
        declaration: KtNamedDeclaration,
        enoughToSearchUsages: Lazy<SearchCostResult>,
        descriptor: DeclarationDescriptor? = null
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
                hasReferences(declaration, descriptor, restrictedScope) ||
                hasOverrides(declaration, restrictedScope) ||
                hasFakeOverrides(declaration, restrictedScope) ||
                hasPlatformImplementations(declaration, descriptor)
    }

    private fun checkDeclaration(declaration: KtNamedDeclaration, importedDeclaration: KtNamedDeclaration): Boolean =
        declaration !in importedDeclaration.parentsWithSelf && !hasNonTrivialUsages(importedDeclaration)

    private val KtNamedDeclaration.isObjectOrEnum: Boolean get() = this is KtObjectDeclaration || this is KtClass && isEnum()

    private fun checkReference(ref: PsiReference, declaration: KtNamedDeclaration, descriptor: DeclarationDescriptor?): Boolean {
        ProgressManager.checkCanceled()
        if (declaration.isAncestor(ref.element)) return true // usages inside element's declaration are not counted

        if (ref.element.parent is KtValueArgumentName) return true // usage of parameter in form of named argument is not counted

        val import = ref.element.getParentOfType<KtImportDirective>(false)
        if (import != null) {
            if (import.aliasName != null && import.aliasName != declaration.name) {
                return false
            }
            // check if we import member(s) from object / nested object / enum and search for their usages
            val originalDeclaration = (descriptor as? TypeAliasDescriptor)?.classDescriptor?.findPsi() as? KtNamedDeclaration
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

    private fun hasReferences(
        declaration: KtNamedDeclaration,
        descriptor: DeclarationDescriptor?,
        useScope: SearchScope
    ): Boolean {
        fun checkReference(ref: PsiReference): Boolean = checkReference(ref, declaration, descriptor)

        val searchOptions = KotlinReferencesSearchOptions(acceptCallableOverrides = declaration.hasActualModifier())
        val searchParameters = KotlinReferencesSearchParameters(
            declaration,
            useScope,
            kotlinOptions = searchOptions
        )
        val referenceUsed: Boolean by lazy { !ReferencesSearch.search(searchParameters).forEach(Processor { checkReference(it) }) }

        if (descriptor is FunctionDescriptor && DescriptorUtils.findJvmNameAnnotation(descriptor) != null) {
            if (referenceUsed) return true
        }

        if (declaration is KtSecondaryConstructor) {
            val containingClass = declaration.containingClass()
            if (containingClass != null && ReferencesSearch.search(KotlinReferencesSearchParameters(containingClass, useScope)).any {
                    it.element.getStrictParentOfType<KtTypeAlias>() != null ||
                            it.element.getStrictParentOfType<KtCallExpression>()?.resolveToCall()?.resultingDescriptor == descriptor
                }) return true
        }

        if (declaration is KtCallableDeclaration && declaration.canBeHandledByLightMethods(descriptor)) {
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

        return referenceUsed || checkPrivateDeclaration(declaration, descriptor)
    }

    private fun hasBuiltInEnumFunctionReference(enumClass: KtClass?, useScope: SearchScope): Boolean {
        if (enumClass == null) return false
        val isFoundEnumFunctionReferenceViaSearch = ReferencesSearch.search(KotlinReferencesSearchParameters(enumClass, useScope))
            .any { hasBuiltInEnumFunctionReference(it, enumClass) }

        return isFoundEnumFunctionReferenceViaSearch || hasEnumFunctionReferenceInEnumClass(enumClass)
    }

    /**
     * Checks calls in enum class without receiver expression. Example: values(), ::values
     */
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
        if ((resolveTargetClass() as? KtUltraLightClass)?.isEnum != true) return false

        fun PsiReference.isQualifiedNameInEnumStaticMethods(): Boolean {
            val referenceExpression = resolve() as? PsiMember ?: return false
            return referenceExpression.containingClass?.kotlinFqName == FqName(importedEnumQualifiedName)
                    && referenceExpression.name in ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES_IN_JAVA.map { it.asString() }
        }

        return containingFile.anyDescendantOfType(PsiReferenceExpression::isQualifiedNameInEnumStaticMethods)
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

    private fun checkPrivateDeclaration(declaration: KtNamedDeclaration, descriptor: DeclarationDescriptor?): Boolean {
        if (descriptor == null || !declaration.isPrivateNestedClassOrObject) return false

        val set = hashSetOf<KtSimpleNameExpression>()
        declaration.containingKtFile.importList?.acceptChildren(simpleNameExpressionRecursiveVisitor {
            set += it
        })

        return set.mapNotNull { it.referenceExpression() }
            .filter { descriptor in it.resolveMainReferenceToDescriptors() }
            .any { !checkReference(it.mainReference, declaration, descriptor) }
    }

    private fun KtCallableDeclaration.canBeHandledByLightMethods(descriptor: DeclarationDescriptor?): Boolean {
        return when {
            descriptor is ConstructorDescriptor -> {
                val classDescriptor = descriptor.constructedClass
                !classDescriptor.isInlineClass() && classDescriptor.visibility != DescriptorVisibilities.LOCAL
            }
            hasModifier(KtTokens.INTERNAL_KEYWORD) -> false
            descriptor !is FunctionDescriptor -> true
            else -> !descriptor.hasInlineClassParameters()
        }
    }

    private fun FunctionDescriptor.hasInlineClassParameters(): Boolean {
        return when {
            dispatchReceiverParameter?.type?.isInlineClassType() == true -> true
            extensionReceiverParameter?.type?.isInlineClassType() == true -> true
            else -> valueParameters.any { it.type.isInlineClassType() }
        }
    }

    private fun hasOverrides(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean =
        DefinitionsScopedSearch.search(declaration, useScope).findFirst() != null

    private fun hasFakeOverrides(declaration: KtNamedDeclaration, useScope: SearchScope): Boolean {
        val ownerClass = declaration.containingClassOrObject as? KtClass ?: return false
        if (!ownerClass.isInheritable()) return false
        val descriptor = declaration.toDescriptor() as? CallableMemberDescriptor ?: return false
        if (descriptor.modality == Modality.ABSTRACT) return false
        val lightMethods = declaration.toLightMethods()
        return DefinitionsScopedSearch.search(ownerClass, useScope).any { element: PsiElement ->

            when (element) {
                is KtLightClass -> {
                    val memberBySignature =
                        (element.kotlinOrigin?.toDescriptor() as? ClassDescriptor)?.findCallableMemberBySignature(descriptor)
                    memberBySignature != null &&
                            !memberBySignature.kind.isReal &&
                            memberBySignature.overriddenDescriptors.any { it != descriptor }
                }
                is PsiClass ->
                    lightMethods.any { lightMethod ->

                        val sameMethods = element.findMethodsBySignature(lightMethod, true)
                        sameMethods.all { it.containingClass != element } &&
                                sameMethods.any { it.containingClass != lightMethod.containingClass }
                    }
                else ->
                    false
            }
        }
    }

    private fun hasPlatformImplementations(declaration: KtNamedDeclaration, descriptor: DeclarationDescriptor?): Boolean {
        if (!declaration.hasExpectModifier()) return false

        if (descriptor !is MemberDescriptor) return false
        val commonModuleDescriptor = declaration.containingKtFile.findModuleDescriptor()

        // TODO: Check if 'allImplementingDescriptors' should be used instead!
        return commonModuleDescriptor.implementingDescriptors.any { it.hasActualsFor(descriptor) } ||
                commonModuleDescriptor.hasActualsFor(descriptor)
    }

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(JavaInspectionControls.button(JavaInspectionButtons.ButtonKind.ENTRY_POINT_ANNOTATIONS))
    }

    private fun createQuickFixes(declaration: KtNamedDeclaration): List<LocalQuickFix> {
        val list = ArrayList<LocalQuickFix>()

        list.add(SafeDeleteFix(declaration))

        for (annotationEntry in declaration.annotationEntries) {
            val resolvedName = annotationEntry.resolveToDescriptorIfAny() ?: continue
            val fqName = resolvedName.fqName?.asString() ?: continue

            // checks taken from com.intellij.codeInspection.util.SpecialAnnotationsUtilBase.createAddToSpecialAnnotationFixes
            if (fqName.startsWith("kotlin.")
                || fqName.startsWith("java.")
                || fqName.startsWith("javax.")
                || fqName.startsWith("org.jetbrains.annotations.")
            )
                continue

            val intentionAction = createAddToDependencyInjectionAnnotationsFix(declaration.project, fqName)

            list.add(IntentionWrapper(intentionAction))
        }

        return list
    }
}

class SafeDeleteFix(declaration: KtDeclaration) : LocalQuickFix {
    @Nls
    private val name: String =
        if (declaration is KtConstructor<*>) KotlinBundle.message("safe.delete.constructor")
        else QuickFixBundle.message("safe.delete.text", declaration.name)

    override fun getName() = name

    override fun getFamilyName() = QuickFixBundle.message("safe.delete.family")

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val declaration = descriptor.psiElement.getStrictParentOfType<KtDeclaration>() ?: return
        if (!FileModificationService.getInstance().prepareFileForWrite(declaration.containingFile)) return
        if (declaration is KtParameter && declaration.parent is KtParameterList && declaration.parent?.parent is KtFunction) {
            RemoveUnusedFunctionParameterFix(declaration).invoke(project, declaration.findExistingEditor(), declaration.containingKtFile)
        } else {
            val declarationPointer = declaration.createSmartPointer()
            invokeLater {
                declarationPointer.element?.let { safeDelete(project, it) }
            }
        }
    }
}

private fun safeDelete(project: Project, declaration: PsiElement) {
    SafeDeleteHandler.invoke(project, arrayOf(declaration), false)
}
