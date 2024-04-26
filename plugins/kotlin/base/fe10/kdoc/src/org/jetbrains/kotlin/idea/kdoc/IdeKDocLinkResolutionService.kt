// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMethodDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isChildOf
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.utils.Printer

class IdeKDocLinkResolutionService(val project: Project) : KDocLinkResolutionService {
    override fun resolveKDocLink(
        context: BindingContext,
        fromDescriptor: DeclarationDescriptor,
        resolutionFacade: ResolutionFacade,
        qualifiedName: List<String>
    ): Collection<DeclarationDescriptor> {

        val scope = KotlinSourceFilterScope.projectAndLibrarySources(GlobalSearchScope.projectScope(project), project)

        val shortName = qualifiedName.lastOrNull() ?: return emptyList()

        val targetFqName = FqName.fromSegments(qualifiedName)

        val functions = KotlinFunctionShortNameIndex.get(shortName, project, scope).asSequence()
        val classes = KotlinClassShortNameIndex.get(shortName, project, scope).asSequence()

        val descriptors = (functions + classes).filter { it.fqName == targetFqName }
            .map { it.unsafeResolveToDescriptor(BodyResolveMode.PARTIAL) } // TODO Filter out not visible due dependencies config descriptors
            .toList()
        if (descriptors.isNotEmpty())
            return descriptors

        val javaClasses =
            JavaShortClassNameIndex.getInstance().getClasses(shortName, project, scope).asSequence()
                .filter { it.kotlinFqName == targetFqName }
                .mapNotNull { it.getJavaClassDescriptor() }
                .toList()
        if (javaClasses.isNotEmpty()) {
            return javaClasses
        }

        val javaFunctions =
            JavaMethodNameIndex.getInstance().getMethods(shortName, project, scope).asSequence()
                .filter { it.kotlinFqName == targetFqName }
                .mapNotNull { it.getJavaMethodDescriptor() }
                .toList()
        if (javaFunctions.isNotEmpty()) {
            return javaFunctions
        }

        if (!targetFqName.isRoot && KotlinPackageIndexUtils.packageExists(targetFqName, scope))
            return listOf(GlobalSyntheticPackageViewDescriptor(targetFqName, project, scope))
        return emptyList()
    }
}

private fun shouldNotBeCalled(): Nothing = throw UnsupportedOperationException("Synthetic PVD for KDoc link resolution")

private class GlobalSyntheticPackageViewDescriptor(
    override val fqName: FqName,
    private val project: Project,
    private val scope: GlobalSearchScope
) : PackageViewDescriptor {
    override fun getContainingDeclaration(): PackageViewDescriptor? =
        if (fqName.isOneSegmentFQN()) null else GlobalSyntheticPackageViewDescriptor(fqName.parent(), project, scope)


    override val memberScope: MemberScope = object : MemberScope {

        override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> = shouldNotBeCalled()

        override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> =
            shouldNotBeCalled()

        override fun getFunctionNames(): Set<Name> = shouldNotBeCalled()
        override fun getVariableNames(): Set<Name> = shouldNotBeCalled()
        override fun getClassifierNames(): Set<Name> = shouldNotBeCalled()

        override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor = shouldNotBeCalled()

        override fun printScopeStructure(p: Printer) {
            p.printIndent()
            p.print("GlobalSyntheticPackageViewDescriptorMemberScope (INDEX)")
        }


        fun getClassesByNameFilter(nameFilter: (Name) -> Boolean) = KotlinFullClassNameIndex
            .getAllKeys(project)
            .asSequence()
            .filter { it.startsWith(fqName.asString()) }
            .map(::FqName)
            .filter { it.isChildOf(fqName) }
            .filter { nameFilter(it.shortName()) }
            .flatMap { KotlinFullClassNameIndex.get(it.asString(), project, scope).asSequence() }
            .map { it.resolveToDescriptorIfAny() }

        fun getFunctionsByNameFilter(nameFilter: (Name) -> Boolean) = KotlinTopLevelFunctionFqnNameIndex
            .getAllKeys(project)
            .asSequence()
            .filter { it.startsWith(fqName.asString()) }
            .map(::FqName)
            .filter { it.isChildOf(fqName) }
            .filter { nameFilter(it.shortName()) }
            .flatMap { KotlinTopLevelFunctionFqnNameIndex.get(it.asString(), project, scope).asSequence() }
            .map { it.resolveToDescriptorIfAny() }

        fun getSubpackages(nameFilter: (Name) -> Boolean) = KotlinPackageIndexUtils.getSubPackageFqNames(fqName, scope, nameFilter)
            .map { GlobalSyntheticPackageViewDescriptor(it, project, scope) }

        override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
        ): Collection<DeclarationDescriptor> = (getClassesByNameFilter(nameFilter) +
                getFunctionsByNameFilter(nameFilter) +
                getSubpackages(nameFilter)
                ).filterNotNull().toList()

    }
    override val module: ModuleDescriptor
        get() = shouldNotBeCalled()
    override val fragments: List<PackageFragmentDescriptor>
        get() = shouldNotBeCalled()

    override fun getOriginal() = this

    override fun getName(): Name = fqName.shortName()

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R = shouldNotBeCalled()

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) = shouldNotBeCalled()

    override val annotations = Annotations.EMPTY
}
