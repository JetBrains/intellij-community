// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import org.jetbrains.kotlin.idea.projectModel.KotlinModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinVariantData
import org.jetbrains.kotlin.idea.projectModel.KotlinFragment

private fun ClassLoader.loadClassOrFail(className: String): Class<*> =
    loadClassOrNull(className) ?: error("Classloader failed to load class '$className'!")

private const val FRAGMENT_GRANULAR_METADATA_RESOLVER_CLASS = "org.jetbrains.kotlin.gradle.plugin.mpp.pm20.FragmentGranularMetadataResolver"

private val ClassLoader.fragmentGranularMetadataResolverClass: Class<*>
    get() = loadClassOrFail(FRAGMENT_GRANULAR_METADATA_RESOLVER_CLASS)

class KotlinProjectModelImportingContext(val project: Project) {
    lateinit var classLoader: ClassLoader
    val modulesById: MutableMap<KotlinModuleIdentifier, KotlinModule> = mutableMapOf()
    val rawModuleById: MutableMap<KotlinModuleIdentifier, Any> = mutableMapOf()
    val fragmentStubsByModuleId: MutableMap<KotlinModuleIdentifier, MutableCollection<KotlinGradleFragmentProto>> = mutableMapOf()
    val rawFragmentsByFragmentStubMap: MutableMap<KotlinGradleFragmentProto, Any> = mutableMapOf()
    val kpmFragmentsByFragmentStubMap: MutableMap<KotlinGradleFragmentProto, KotlinFragment> = mutableMapOf()
    val metadataResolverByFragmentStubMap: MutableMap<KotlinGradleFragmentProto, Any> = mutableMapOf()
    val variantStubsWithData: MutableMap<KotlinGradleFragmentProto, KotlinVariantData> = mutableMapOf()
}

internal fun KotlinProjectModelImportingContext.initializeModule(module: KotlinModule, rawModule: Any) = with(module.moduleIdentifier) {
    modulesById[this] = module
    rawModuleById[this] = rawModule
    fragmentStubsByModuleId[this] = arrayListOf()
}

internal fun KotlinProjectModelImportingContext.cleanupModule(module: KotlinModule) = with(module.moduleIdentifier) {
    modulesById.remove(this)
    rawModuleById.remove(this)
    fragmentStubsByModuleId[this]?.forEach { proto ->
        rawFragmentsByFragmentStubMap.remove(proto)
        kpmFragmentsByFragmentStubMap.remove(proto)
        metadataResolverByFragmentStubMap.remove(proto)
        variantStubsWithData.remove(proto)
    }
    fragmentStubsByModuleId.remove(this)
}

internal val KotlinProjectModelImportingContext.variantsAsList
    get() = variantStubsWithData.mapKeys { kpmFragmentsByFragmentStubMap.getValue(it.key) }.toList()


internal fun KotlinProjectModelImportingContext.initializeFragmentProto(fragmentStub: KotlinGradleFragmentProto, rawFragment: Any) =
    with(fragmentStub.containingModuleIdentifier) {
        fragmentStubsByModuleId.getValue(this).add(fragmentStub)
        rawFragmentsByFragmentStubMap[fragmentStub] = rawFragment
    }

internal fun KotlinProjectModelImportingContext.initializeVariantStub(
    variantStub: KotlinGradleFragmentProto,
    variantData: KotlinVariantData
) {
    variantStubsWithData[variantStub] = variantData
}

fun KotlinProjectModelImportingContext.initializeFragmentGranularMetadataResolvers(refinesMap: Map<KotlinGradleFragmentProto, Collection<KotlinGradleFragmentProto>>) {
    val fragmentGranularMetadataResolverClass = classLoader.fragmentGranularMetadataResolverClass
    refinesMap.entries.sortedBy { it.value.size }.forEach { (fragment, refinesFragments) ->
        val originKotlinGradleFragment = rawFragmentsByFragmentStubMap.getValue(fragment)
        //After topological sort we expect that resolvers for all refines fragment nodes were already added to map
        val parentMetadataResolvers = refinesFragments.map { metadataResolverByFragmentStubMap.getValue(it) }
        val fragmentGranularMetadataResolver = fragmentGranularMetadataResolverClass.constructors.first()
                .newInstance(originKotlinGradleFragment, lazy { parentMetadataResolvers })
        metadataResolverByFragmentStubMap[fragment] = fragmentGranularMetadataResolver
    }
}