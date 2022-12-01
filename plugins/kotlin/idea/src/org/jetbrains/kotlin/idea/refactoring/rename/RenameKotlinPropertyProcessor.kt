// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pass
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.*
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.checkSuperMethodsWithPopup
import org.jetbrains.kotlin.idea.refactoring.dropOverrideKeywordIfNecessary
import org.jetbrains.kotlin.idea.references.KtDestructuringDeclarationReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.base.util.codeUsageScope
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RenameKotlinPropertyProcessor : RenameKotlinPsiProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val namedUnwrappedElement = element.namedUnwrappedElement
        return namedUnwrappedElement is KtProperty || namedUnwrappedElement is PropertyMethodWrapper ||
                (namedUnwrappedElement is KtParameter && namedUnwrappedElement.hasValOrVar())
    }

    override fun isToSearchInComments(psiElement: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY

    override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY = enabled
    }

    override fun isToSearchForTextOccurrences(element: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PROPERTY

    override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PROPERTY = enabled
    }

    private fun getJvmNames(element: PsiElement): Pair<String?, String?> {
        val descriptor = (element.unwrapped as? KtDeclaration)?.unsafeResolveToDescriptor() as? PropertyDescriptor ?: return null to null
        val getterName = descriptor.getter?.let { DescriptorUtils.getJvmName(it) }
        val setterName = descriptor.setter?.let { DescriptorUtils.getJvmName(it) }
        return getterName to setterName
    }

    protected fun processFoundReferences(
        element: PsiElement,
        allReferences: Collection<PsiReference>
    ): Collection<PsiReference> {
        val references = allReferences.filterNot { it is KtDestructuringDeclarationReference }
        val (getterJvmName, setterJvmName) = getJvmNames(element)
        return when {
            getterJvmName == null && setterJvmName == null -> references
            element is KtElement -> references.filter {
                it is KtReference || (getterJvmName == null && (it.resolve() as? PsiNamedElement)?.name != setterJvmName) || (setterJvmName == null && (it.resolve() as? PsiNamedElement)?.name != getterJvmName)
            }
            element is KtLightDeclaration<*, *> -> {
                val name = element.name
                if (name == getterJvmName || name == setterJvmName) references.filterNot { it is KtReference } else references
            }
            else -> emptyList()
        }
    }

    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<out PsiElement, String>,
        result: MutableList<UsageInfo>
    ) {
        val declaration = element.namedUnwrappedElement as? KtNamedDeclaration ?: return

        val collisions = SmartList<UsageInfo>()
        renameRefactoringSupport.checkRedeclarations(declaration, newName, collisions)
        renameRefactoringSupport.checkAccidentalPropertyOverrides(declaration, newName, collisions)
        renameRefactoringSupport.checkOriginalUsagesRetargeting(declaration, newName, result, collisions)
        renameRefactoringSupport.checkNewNameUsagesRetargeting(declaration, newName, collisions)
        result += collisions
    }

    private fun chooseCallableToRename(callableDeclaration: KtCallableDeclaration): KtCallableDeclaration? {
        val deepestSuperDeclaration = findDeepestOverriddenDeclaration(callableDeclaration)
        if (deepestSuperDeclaration == null || deepestSuperDeclaration == callableDeclaration) {
            return callableDeclaration
        }

        if (isUnitTestMode()) return deepestSuperDeclaration

        val containsText: String? =
            deepestSuperDeclaration.fqName?.parent()?.asString() ?: (deepestSuperDeclaration.parent as? KtClassOrObject)?.name

        val message = if (containsText != null)
            KotlinBundle.message("text.do.you.want.to.rename.base.property.from.0", containsText)
        else
            KotlinBundle.message("text.do.you.want.to.rename.base.property")

        val result = Messages.showYesNoCancelDialog(
            deepestSuperDeclaration.project,
            message,
            KotlinBundle.message("title.rename.warning"),
            Messages.getQuestionIcon()
        )

        return when (result) {
            Messages.YES -> deepestSuperDeclaration
            Messages.NO -> callableDeclaration
            else -> /* Cancel rename */ null
        }
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        val namedUnwrappedElement = element.namedUnwrappedElement ?: return null

        val callableDeclaration = namedUnwrappedElement as? KtCallableDeclaration
            ?: throw IllegalStateException("Can't be for element $element there because of canProcessElement()")

        val declarationToRename = chooseCallableToRename(callableDeclaration) ?: return null

        val (getterJvmName, setterJvmName) = getJvmNames(namedUnwrappedElement)
        if (element is KtLightMethod) {
            val name = element.name
            if (element.name != getterJvmName && element.name != setterJvmName) return declarationToRename
            return declarationToRename.toLightMethods().firstOrNull { it.name == name }
        }

        return declarationToRename
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<in PsiElement>) {
        val namedUnwrappedElement = element.namedUnwrappedElement ?: return

        val callableDeclaration = namedUnwrappedElement as? KtCallableDeclaration
            ?: throw IllegalStateException("Can't be for element $element there because of canProcessElement()")

        fun preprocessAndPass(substitutedJavaElement: PsiElement) {
            val (getterJvmName, setterJvmName) = getJvmNames(namedUnwrappedElement)
            val elementToProcess = if (element is KtLightMethod) {
                val name = element.name
                if (element.name != getterJvmName && element.name != setterJvmName) {
                    substitutedJavaElement
                } else {
                    substitutedJavaElement.toLightMethods().firstOrNull { it.name == name }
                }
            } else
                substitutedJavaElement

            renameCallback.pass(elementToProcess)
        }

        val deepestSuperDeclaration = findDeepestOverriddenDeclaration(callableDeclaration)
        if (deepestSuperDeclaration == null || deepestSuperDeclaration == callableDeclaration) {
            return preprocessAndPass(callableDeclaration)
        }

        val superPsiMethods = listOfNotNull(deepestSuperDeclaration.getRepresentativeLightMethod())
        checkSuperMethodsWithPopup(callableDeclaration, superPsiMethods, editor) {
            preprocessAndPass(if (it.size > 1) deepestSuperDeclaration else callableDeclaration)
        }
    }

    class PropertyMethodWrapper(private val propertyMethod: PsiMethod) : PsiNamedElement by propertyMethod,
                                                                         NavigationItem by propertyMethod {
        override fun getName() = propertyMethod.name
        override fun setName(name: String) = this
        override fun copy() = this
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        super.prepareRenaming(element, newName, allRenames, scope)

        val namedUnwrappedElement = element.namedUnwrappedElement
        val propertyMethods = when (namedUnwrappedElement) {
            is KtProperty -> runReadAction { LightClassUtil.getLightClassPropertyMethods(namedUnwrappedElement) }
            is KtParameter -> runReadAction { LightClassUtil.getLightClassPropertyMethods(namedUnwrappedElement) }
            else -> throw IllegalStateException("Can't be for element $element there because of canProcessElement()")
        }

        val newPropertyName = if (element is KtLightMethod) propertyNameByAccessor(newName, element) else newName

        val (getterJvmName, setterJvmName) = getJvmNames(namedUnwrappedElement)

        val getter = propertyMethods.getter as? KtLightMethod
        val setter = propertyMethods.setter as? KtLightMethod
        if (newPropertyName != null
            && getter != null && setter != null
            && (element == getter || element == setter)
            && propertyNameByAccessor(getter.name, getter) == propertyNameByAccessor(setter.name, setter)
        ) {
            val accessorToRename = if (element == getter) setter else getter
            val newAccessorName = if (element == getter) JvmAbi.setterName(newPropertyName) else JvmAbi.getterName(newPropertyName)
            if (isUnitTestMode() || Messages.showYesNoDialog(
                    KotlinBundle.message("text.do.you.want.to.rename.0.as.well", accessorToRename.name),
                    RefactoringBundle.message("rename.title"),
                    Messages.getQuestionIcon()
                ) == Messages.YES
            ) {
                allRenames[accessorToRename] = newAccessorName
            }
        }

        for (propertyMethod in propertyMethods) {
            val mangledPropertyName = if (propertyMethod is KtLightMethod && propertyMethod.isMangled) {
                val suffix = KotlinTypeMapper.InternalNameMapper.getModuleNameSuffix(propertyMethod.name)
                if (suffix != null && newPropertyName != null) KotlinTypeMapper.InternalNameMapper.mangleInternalName(
                    newPropertyName,
                    suffix
                ) else null
            } else null

            val adjustedPropertyName = mangledPropertyName ?: newPropertyName
            if (element is KtDeclaration && adjustedPropertyName != null) {
                val wrapper = PropertyMethodWrapper(propertyMethod)
                when {
                    JvmAbi.isGetterName(propertyMethod.name) && getterJvmName == null ->
                        allRenames[wrapper] = JvmAbi.getterName(adjustedPropertyName)

                    JvmAbi.isSetterName(propertyMethod.name) && setterJvmName == null ->
                        allRenames[wrapper] = JvmAbi.setterName(adjustedPropertyName)
                }
            }

            addRenameElements(propertyMethod, (element as PsiNamedElement).name, adjustedPropertyName, allRenames, scope)
        }

        renameRefactoringSupport.prepareForeignUsagesRenaming(element, newName, allRenames, scope)
    }

    protected enum class UsageKind {
        SIMPLE_PROPERTY_USAGE,
        GETTER_USAGE,
        SETTER_USAGE
    }

    private fun addRenameElements(
        psiMethod: PsiMethod,
        oldName: String?,
        newName: String?,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        val allOverriders = runProcessWithProgressSynchronously(
            KotlinBundle.message("rename.searching.for.all.overrides"),
            canBeCancelled = true,
            psiMethod.project,
        ) {
            OverridingMethodsSearch.search(psiMethod, scope, true).findAll()
        }

        for (overrider in allOverriders) {
            val overriderElement = overrider.namedUnwrappedElement

            if (overriderElement != null && overriderElement !is SyntheticElement) {
                RenameUtil.assertNonCompileElement(overriderElement)

                val overriderName = overriderElement.name

                if (overriderElement is PsiMethod) {
                    if (newName != null && Name.isValidIdentifier(newName)) {
                        val isGetter = overriderElement.parameterList.parametersCount == 0
                        allRenames[overriderElement] = if (isGetter) JvmAbi.getterName(newName) else JvmAbi.setterName(newName)
                    }
                } else {
                    val demangledName =
                        if (newName != null && overrider is KtLightMethod && overrider.isMangled) KotlinTypeMapper.InternalNameMapper.demangleInternalName(
                            newName
                        ) else null
                    val adjustedName = demangledName ?: newName
                    val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, oldName, adjustedName)
                    if (newOverriderName != null) {
                        allRenames[overriderElement] = newOverriderName
                    }
                }
            }
        }
    }

    private fun findDeepestOverriddenDeclaration(declaration: KtCallableDeclaration): KtCallableDeclaration? {
        if (declaration.modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true) {
            val deepestSuperDeclarations = KotlinSearchUsagesSupport.findDeepestSuperMethodsNoWrapping(declaration)

            // Take one of supers for now - API doesn't support substitute to several elements (IDEA-48796)
            return deepestSuperDeclarations
                .filterIsInstance<KtCallableDeclaration>()
                .firstOrNull()
        }

        return null
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        val referenceSearchScope = if (element is KtParameter && element.isPrivate()) {
            element.ownerFunction.safeAs<KtPrimaryConstructor>()?.codeUsageScope()?.union(searchScope) ?: searchScope
        } else {
            searchScope
        }
        val references = super.findReferences(element, referenceSearchScope, searchInCommentsAndStrings)
        return processFoundReferences(element, references)
    }

    //TODO: a very long and complicated method, even recursive. mb refactor it somehow? at least split by PsiElement types?
    override tailrec fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        val newNameUnquoted = newName.unquoteKotlinIdentifier()
        if (element is KtLightMethod) {
            if (element.modifierList.hasAnnotation(DescriptorUtils.JVM_NAME.asString())) {
                return super.renameElement(element, newName, usages, listener)
            }

            val origin = element.kotlinOrigin
            val newPropertyName = propertyNameByAccessor(newNameUnquoted, element)
            // Kotlin references to Kotlin property should not use accessor name
            if (newPropertyName != null && (origin is KtProperty || origin is KtParameter)) {
                val (ktUsages, otherUsages) = usages.partition { it.reference is KtSimpleNameReference }
                super.renameElement(element, newName, otherUsages.toTypedArray(), listener)
                renameElement(origin, newPropertyName.quoteIfNeeded(), ktUsages.toTypedArray(), listener)
                return
            }
        }

        if (element !is KtProperty && element !is KtParameter) {
            super.renameElement(element, newName, usages, listener)
            return
        }

        val name = (element as KtNamedDeclaration).name!!
        val oldGetterName = JvmAbi.getterName(name)
        val oldSetterName = JvmAbi.setterName(name)

        if (KotlinPsiHeuristics.isEnumCompanionPropertyWithEntryConflict(element, newNameUnquoted)) {
            for ((i, usage) in usages.withIndex()) {
                if (usage !is MoveRenameUsageInfo) continue
                val ref = usage.reference ?: continue
                // TODO: Enum value can't be accessed from Java in case of conflict with companion member
                if (ref is KtReference) {
                    val newRef = (ref.bindToElement(element) as? KtSimpleNameExpression)?.mainReference ?: continue
                    usages[i] = MoveRenameUsageInfo(newRef, usage.referencedElement)
                }
            }
        }

        val adjustedUsages = if (element is KtParameter) usages.filterNot {
            val refTarget = it.reference?.resolve()
            refTarget is KtLightMethod && DataClassResolver.isComponentLike(Name.guessByFirstCharacter(refTarget.name))
        } else usages.toList()

        val refKindUsages = adjustedUsages.groupBy { usage: UsageInfo ->
            val refElement = usage.reference?.resolve()
            if (refElement is PsiMethod) {
                val refElementName = refElement.name
                val refElementNameToCheck = (
                        if (usage is MangledJavaRefUsageInfo)
                            KotlinTypeMapper.InternalNameMapper.demangleInternalName(refElementName)
                        else
                            null
                        ) ?: refElementName

                when (refElementNameToCheck) {
                    oldGetterName -> UsageKind.GETTER_USAGE
                    oldSetterName -> UsageKind.SETTER_USAGE
                    else -> UsageKind.SIMPLE_PROPERTY_USAGE
                }
            } else {
                UsageKind.SIMPLE_PROPERTY_USAGE
            }
        }

        super.renameElement(
            element.copy(), JvmAbi.setterName(newNameUnquoted).quoteIfNeeded(),
            refKindUsages[UsageKind.SETTER_USAGE]?.toTypedArray() ?: arrayOf(),
            null,
        )

        super.renameElement(
            element.copy(), JvmAbi.getterName(newNameUnquoted).quoteIfNeeded(),
            refKindUsages[UsageKind.GETTER_USAGE]?.toTypedArray() ?: arrayOf(),
            null,
        )

        super.renameElement(
            element, newName,
            refKindUsages[UsageKind.SIMPLE_PROPERTY_USAGE]?.toTypedArray() ?: arrayOf(),
            null,
        )

        usages.forEach { (it as? KtResolvableCollisionUsageInfo)?.apply() }

        dropOverrideKeywordIfNecessary(element)

        listener?.elementRenamed(element)
    }

}
