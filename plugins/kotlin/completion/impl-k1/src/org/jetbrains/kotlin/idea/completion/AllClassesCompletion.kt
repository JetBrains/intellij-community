// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.AllClassesGetter
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.PsiClass
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.isSyntheticKotlinClass
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

class AllClassesCompletion(
    private val parameters: CompletionParameters,
    private val kotlinIndicesHelper: KotlinIndicesHelper,
    private val prefixMatcher: PrefixMatcher,
    private val resolutionFacade: ResolutionFacade,
    private val kindFilter: (ClassKind) -> Boolean,
    private val includeTypeAliases: Boolean,
    private val includeJavaClassesNotToBeUsed: Boolean
) {
    fun collect(classifierDescriptorCollector: (ClassifierDescriptorWithTypeParameters) -> Unit, javaClassCollector: (PsiClass) -> Unit) {

        //TODO: this is a temporary solution until we have built-ins in indices
        // we need only nested classes because top-level built-ins are all added through default imports
        for (builtInPackage in resolutionFacade.moduleDescriptor.builtIns.builtInPackagesImportedByDefault) {
            collectClassesFromScope(builtInPackage.memberScope) {
                if (it.containingDeclaration is ClassDescriptor) {
                    classifierDescriptorCollector(it)
                }
            }
        }

        kotlinIndicesHelper.processKotlinClasses(
            { prefixMatcher.prefixMatches(it) },
            kindFilter = kindFilter,
            processor = classifierDescriptorCollector
        )

        if (includeTypeAliases) {
            kotlinIndicesHelper.processTopLevelTypeAliases(prefixMatcher.asStringNameFilter(), classifierDescriptorCollector)
        }

        if ((parameters.originalFile as KtFile).platform.isJvm()) {
            addAdaptedJavaCompletion(javaClassCollector)
        }
    }

    private fun collectClassesFromScope(scope: MemberScope, collector: (ClassDescriptor) -> Unit) {
        for (descriptor in scope.getDescriptorsFiltered(DescriptorKindFilter.CLASSIFIERS)) {
            if (descriptor is ClassDescriptor) {
                if (kindFilter(descriptor.kind) && prefixMatcher.prefixMatches(descriptor.name.asString())) {
                    collector(descriptor)
                }

                collectClassesFromScope(descriptor.unsubstitutedInnerClassesScope, collector)
            }
        }
    }

    private fun addAdaptedJavaCompletion(collector: (PsiClass) -> Unit) {
        val shortNamesCache = PsiShortNamesCache.EP_NAME.getExtensions(parameters.editor.project).firstOrNull {
            it is KotlinShortNamesCache
        } as KotlinShortNamesCache?
        shortNamesCache?.disableSearch?.set(true)
        try {
            AllClassesGetter.processJavaClasses(parameters, prefixMatcher, true) { psiClass ->
                if (psiClass!! !is KtLightClass) { // Kotlin class should have already been added as kotlin element before
                    if (psiClass.isSyntheticKotlinClass()) return@processJavaClasses // filter out synthetic classes produced by Kotlin compiler

                    val kind = when {
                        psiClass.isAnnotationType -> ClassKind.ANNOTATION_CLASS
                        psiClass.isInterface -> ClassKind.INTERFACE
                        psiClass.isEnum -> ClassKind.ENUM_CLASS
                        else -> ClassKind.CLASS
                    }
                    if (kindFilter(kind) && !isNotToBeUsed(psiClass)) {
                        collector(psiClass)
                    }
                }
            }
        } finally {
            shortNamesCache?.disableSearch?.set(false)
        }
    }

  private fun isNotToBeUsed(javaClass: PsiClass): Boolean {
    if (includeJavaClassesNotToBeUsed) return false
    val fqName = javaClass.kotlinFqName
    return fqName?.isJavaClassNotToBeUsedInKotlin() == true
  }
}
