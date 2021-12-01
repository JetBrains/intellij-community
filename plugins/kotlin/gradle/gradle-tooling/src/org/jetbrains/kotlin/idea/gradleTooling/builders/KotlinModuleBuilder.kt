// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinModuleReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinModule

object KotlinModuleBuilder : KotlinProjectModelComponentBuilder<KotlinModuleReflection, KotlinModule> {
    override fun buildComponent(origin: KotlinModuleReflection, importingContext: KotlinProjectModelImportingContext): KotlinModule? {
        val kotlinModuleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(origin.moduleIdentifier ?: return null) ?: return null

        return KotlinGradleModule(
            fragments = emptyList(),
            variants = emptyList(),
            moduleIdentifier = kotlinModuleIdentifier
        ).apply {
            importingContext.initializeModule(this, origin)

            origin.fragments?.forEach { KotlinGradleFragmentProtoBuilder.buildComponent(it, importingContext) }
            origin.variants?.forEach {  KotlinGradleVariantDataBuilder.buildComponent(it, importingContext) }

            val refinesMap = importingContext.fragmentStubsByModuleId.getValue(kotlinModuleIdentifier)
                .associateWith { it.directRefinesDependencies }


            val fragmentImpls = refinesMap.entries.sortedBy { it.value.size }.map { (fragmentProto, refinesFragmentProtos) ->
                val refinesKotlinGradleFragments = refinesFragmentProtos.map { importingContext.kpmFragmentsByFragmentStubMap.getValue(it) }
                val fragmentImpl = fragmentProto.buildKotlinFragment(refinesKotlinGradleFragments)
                importingContext.kpmFragmentsByFragmentStubMap[fragmentProto] = fragmentImpl
                fragmentImpl
            }.distinct()

            fragments = fragmentImpls
            variants = importingContext.variantsAsList
            importingContext.cleanupModule(this)
        }
    }
}
