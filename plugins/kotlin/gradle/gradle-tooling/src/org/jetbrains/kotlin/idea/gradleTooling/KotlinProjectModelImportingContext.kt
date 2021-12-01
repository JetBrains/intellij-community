// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinFragmentReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinIdeFragmentDependencyResolverReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinModuleReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinFragment
import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import org.jetbrains.kotlin.idea.projectModel.KotlinModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinVariantData

class KotlinProjectModelImportingContext(
    val project: Project,
    val classLoader: ClassLoader
) {
    val fragmentDependencyResolver by lazy { KotlinIdeFragmentDependencyResolverReflection.newInstance(project, classLoader) }
    val modulesById: MutableMap<KotlinModuleIdentifier, KotlinModule> = mutableMapOf()
    val rawModuleById: MutableMap<KotlinModuleIdentifier, KotlinModuleReflection> = mutableMapOf()
    val fragmentStubsByModuleId: MutableMap<KotlinModuleIdentifier, MutableCollection<KotlinGradleFragmentProto>> = mutableMapOf()
    val rawFragmentsByFragmentStubMap: MutableMap<KotlinGradleFragmentProto, KotlinFragmentReflection> = mutableMapOf()
    val kpmFragmentsByFragmentStubMap: MutableMap<KotlinGradleFragmentProto, KotlinFragment> = mutableMapOf()
    val variantStubsWithData: MutableMap<KotlinGradleFragmentProto, KotlinVariantData> = mutableMapOf()
}

internal fun KotlinProjectModelImportingContext.initializeModule(module: KotlinModule, rawModule: KotlinModuleReflection) =
    with(module.moduleIdentifier) {
        modulesById[this] = module
        rawModuleById[this] = rawModule
        fragmentStubsByModuleId[this] = arrayListOf()
    }

internal fun KotlinProjectModelImportingContext.cleanupModule(module: KotlinModule) =
    with(module.moduleIdentifier) {
        modulesById.remove(this)
        rawModuleById.remove(this)
        fragmentStubsByModuleId[this]?.forEach { proto ->
            rawFragmentsByFragmentStubMap.remove(proto)
            kpmFragmentsByFragmentStubMap.remove(proto)
            variantStubsWithData.remove(proto)
        }
        fragmentStubsByModuleId.remove(this)
    }

internal val KotlinProjectModelImportingContext.variantsAsList: List<Pair<KotlinFragment, KotlinVariantData>>
    get() = variantStubsWithData.mapKeys { kpmFragmentsByFragmentStubMap.getValue(it.key) }.toList()


internal fun KotlinProjectModelImportingContext.initializeFragmentProto(
    fragmentStub: KotlinGradleFragmentProto, rawFragment: KotlinFragmentReflection
) = with(fragmentStub.containingModuleIdentifier) {
    fragmentStubsByModuleId.getValue(this).add(fragmentStub)
    rawFragmentsByFragmentStubMap[fragmentStub] = rawFragment
}

internal fun KotlinProjectModelImportingContext.initializeVariantStub(
    variantStub: KotlinGradleFragmentProto,
    variantData: KotlinVariantData
) {
    variantStubsWithData[variantStub] = variantData
}
