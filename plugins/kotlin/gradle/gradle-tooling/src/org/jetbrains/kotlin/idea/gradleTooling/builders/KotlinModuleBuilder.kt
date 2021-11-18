// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.initializeModule
import org.jetbrains.kotlin.idea.projectModel.KotlinModule

object KotlinModuleBuilder : KotlinProjectModelComponentBuilder<KotlinModule> {
    override fun buildComponent(origin: Any, importingContext: KotlinProjectModelImportingContext): KotlinModule? {
        val moduleIdentifier = origin["getModuleIdentifier"] ?: return null
        val kotlinModuleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(moduleIdentifier) ?: return null

        return KotlinGradleModule(
            fragments = emptyList(),
            variants = emptyList(),
            moduleIdentifier = kotlinModuleIdentifier
        ).apply {
            importingContext.initializeModule(this, origin)

            origin.extractFragments().forEach { KotlinGradleFragmentProtoBuilder.buildComponent(it, importingContext) }
            origin.extractVariants().forEach { KotlinGradleVariantDataBuilder.buildComponent(it, importingContext) }

            val refinesMap = importingContext.fragmentStubsByModuleId.getValue(kotlinModuleIdentifier)
                .associateWith { it.directRefinesDependencies }
            importingContext.initializeFragmentGranularMetadataResolvers(refinesMap)
            val fragmentMetadataResolutionsByStub = importingContext.resolveAllFragmentMetadataResolutions
            val fragmentImpls = refinesMap.entries.sortedBy { it.value.size }.map { (fragmentProto, refinesFragmentProtos) ->
                val transformedDependencyResolutions = fragmentMetadataResolutionsByStub.getValue(fragmentProto)
                    .flatMap { KotlinFragmentDependencyResolutionBuilder.buildComponent(it, importingContext) }
                val refinesKotlinGradleFragments = refinesFragmentProtos.map { importingContext.kpmFragmentsByFragmentStubMap.getValue(it) }
                val fragmentImpl = fragmentProto.buildKotlinFragment(refinesKotlinGradleFragments, transformedDependencyResolutions)
                importingContext.kpmFragmentsByFragmentStubMap[fragmentProto] = fragmentImpl
                fragmentImpl
            }.distinct()

            fragments = fragmentImpls
            variants = importingContext.variantsAsList
            importingContext.cleanupModule(this)
        }
    }

    private val KotlinProjectModelImportingContext.resolveAllFragmentMetadataResolutions: Map<KotlinGradleFragmentProto, Iterable<Any>>
        get() = metadataResolverByFragmentStubMap.mapValues {
            @Suppress("UNCHECKED_CAST")
            it.value["getResolutions"] as Iterable<Any>
        }.toMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any.extractFragments(): Iterable<Any> = (this["getFragments"] as? Iterable<Named>)?.toList() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Any.extractVariants(): Iterable<Any> = (this["getVariants"] as? Iterable<Named>)?.toList() ?: emptyList()
}