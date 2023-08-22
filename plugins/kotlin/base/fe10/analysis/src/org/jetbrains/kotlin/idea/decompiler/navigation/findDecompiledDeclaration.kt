// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtDecompiledFile
import org.jetbrains.kotlin.analysis.decompiler.psi.text.ByDescriptorIndexer
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.BinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.caches.project.binariesScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.error.ErrorUtils

fun findDecompiledDeclaration(
    project: Project,
    referencedDescriptor: DeclarationDescriptor,
    // TODO: should not require explicitly specified scope to search for builtIns, use SourceElement to provide such information
    builtInsSearchScope: GlobalSearchScope?
): KtDeclaration? {
    if (ErrorUtils.isError(referencedDescriptor)) return null
    if (isLocal(referencedDescriptor)) return null
    if (referencedDescriptor is PackageFragmentDescriptor || referencedDescriptor is PackageViewDescriptor) return null

    val binaryInfo = referencedDescriptor.module.getCapability(ModuleInfo.Capability) as? BinaryModuleInfo

    binaryInfo?.binariesScope?.let {
        return findInScope(referencedDescriptor, it)
    }
    if (KotlinBuiltIns.isBuiltIn(referencedDescriptor)) {
        // builtin module does not contain information about it's origin
        return builtInsSearchScope?.let { findInScope(referencedDescriptor, it) }
        // fallback on searching everywhere since builtIns are accessible from any context
            ?: findInScope(referencedDescriptor, GlobalSearchScope.allScope(project))
            ?: findInScope(referencedDescriptor, GlobalSearchScope.everythingScope(project))
    }
    return null
}

private fun findInScope(referencedDescriptor: DeclarationDescriptor, scope: GlobalSearchScope): KtDeclaration? {
    val project = scope.project ?: return null
    val decompiledFiles = findCandidateDeclarationsInIndex(
        referencedDescriptor.original, KotlinSourceFilterScope.libraryClasses(scope, project), project
    ).mapNotNullTo(LinkedHashSet()) {
        it?.containingFile as? KtDecompiledFile
    }

    return decompiledFiles.asSequence().mapNotNull { file ->
        ByDescriptorIndexer.getDeclarationForDescriptor(referencedDescriptor, file)
    }.firstOrNull()
}

private fun isLocal(descriptor: DeclarationDescriptor): Boolean = if (descriptor is ParameterDescriptor) {
    isLocal(descriptor.containingDeclaration)
} else {
    DescriptorUtils.isLocal(descriptor)
}

private fun findCandidateDeclarationsInIndex(
    referencedDescriptor: DeclarationDescriptor,
    scope: GlobalSearchScope,
    project: Project
): Collection<KtDeclaration?> {
    val containingClass = DescriptorUtils.getParentOfType(referencedDescriptor, ClassDescriptor::class.java, false)
    if (containingClass != null) {
        return KotlinFullClassNameIndex.get(containingClass.fqNameSafe.asString(), project, scope)
    }

    val topLevelDeclaration =
        DescriptorUtils.getParentOfType(referencedDescriptor, PropertyDescriptor::class.java, false) as DeclarationDescriptor?
            ?: DescriptorUtils.getParentOfType(referencedDescriptor, TypeAliasConstructorDescriptor::class.java, false)?.typeAliasDescriptor
            ?: DescriptorUtils.getParentOfType(referencedDescriptor, FunctionDescriptor::class.java, false)
            ?: DescriptorUtils.getParentOfType(referencedDescriptor, TypeAliasDescriptor::class.java, false)
            ?: return emptyList()

    // filter out synthetic descriptors
    if (!DescriptorUtils.isTopLevelDeclaration(topLevelDeclaration)) return emptyList()

    val fqName = topLevelDeclaration.fqNameSafe.asString()
    return when (topLevelDeclaration) {

        is FunctionDescriptor -> KotlinTopLevelFunctionFqnNameIndex.get(fqName, project, scope)

        is PropertyDescriptor -> KotlinTopLevelPropertyFqnNameIndex.get(fqName, project, scope)

        is TypeAliasDescriptor -> KotlinTopLevelTypeAliasFqNameIndex.get(fqName, project, scope)

        else -> error("Referenced non local declaration that is not inside top level function, property, class or typealias:\n $referencedDescriptor")
    }
}
