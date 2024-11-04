// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingMethod
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi

internal class K1RenameRefactoringSupport : KotlinRenameRefactoringSupport {
    override fun checkUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
    ) {

        checkOriginalUsagesRetargeting(declaration, newName, originalUsages, newUsages)
        checkNewNameUsagesRetargeting(declaration, newName, newUsages)
    }

    override fun getAllOverridenFunctions(function: KtNamedFunction): List<PsiElement> {
        val descriptor = function.unsafeResolveToDescriptor() as FunctionDescriptor
        return descriptor.overriddenDescriptors.mapNotNull { it.source.getPsi() }
    }

    override fun mangleInternalName(name: String, moduleName: String): String {
        return KotlinTypeMapper.InternalNameMapper.mangleInternalName(name, moduleName)
    }

    override fun demangleInternalName(mangledName: String): String? {
        return KotlinTypeMapper.InternalNameMapper.demangleInternalName(mangledName)
    }

    override fun getJvmName(element: PsiElement): String? {
        val descriptor = (element.unwrapped as? KtFunction)?.unsafeResolveToDescriptor() as? FunctionDescriptor ?: return null
        return DescriptorUtils.getJvmName(descriptor)
    }

    override fun getJvmNamesForPropertyAccessors(element: PsiElement): Pair<String?, String?> {
        val descriptor = (element.unwrapped as? KtDeclaration)?.unsafeResolveToDescriptor() as? PropertyDescriptor ?: return null to null
        val getterName = descriptor.getter?.let { DescriptorUtils.getJvmName(it) }
        val setterName = descriptor.setter?.let { DescriptorUtils.getJvmName(it) }
        return getterName to setterName
    }

    override fun isCompanionObjectClassReference(psiReference: PsiReference): Boolean {
        if (psiReference !is KtSimpleNameReference) {
            return false
        }
        val bindingContext = psiReference.element.analyze(BodyResolveMode.PARTIAL)
        return bindingContext[BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT, psiReference.element] != null
    }

    override fun shortenReferencesLater(element: KtElement) {
        element.addToShorteningWaitSet(ShortenReferences.Options.ALL_ENABLED)
    }

    override fun findAllOverridingMethods(element: PsiElement, scope: SearchScope): List<PsiMethod> {
      val psiMethods = ((element as? PsiMethod)?.let { listOf(it) } ?: runReadAction { getMethods(element) }) ?: return emptyList()
      return buildList {
          for (m in psiMethods) {
              m.forEachOverridingMethod(scope) {
                  add(it)
                  true
              }
          }
      }
    }

    private fun getMethods(element: PsiElement): List<PsiMethod>? {
        return if (element is KtFunction) {
            LightClassUtil.getLightClassMethod(element)?.let { listOf(it) }
        } else if (element is KtProperty) {
            val propertyMethods = LightClassUtil.getLightClassPropertyMethods(element)
            buildList {
                addIfNotNull(propertyMethods.getter)
                addIfNotNull(propertyMethods.setter)
            }.takeIf { it.isNotEmpty() }
        } else null
    }

    override fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration) {
        org.jetbrains.kotlin.idea.refactoring.dropOverrideKeywordIfNecessary(element)
    }

    override fun isLightClassForRegularKotlinClass(element: KtLightClass): Boolean {
        return element is KtLightClassForSourceDeclaration
    }
}