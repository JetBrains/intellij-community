// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleFragmentProto
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.initializeFragmentProto
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinFragmentReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinModule.Companion.TEST_MODULE_NAME
import java.io.File

object KotlinGradleFragmentProtoBuilder : KotlinProjectModelComponentBuilder<KotlinFragmentReflection, KotlinGradleFragmentProto> {
    override fun buildComponent(
      origin: KotlinFragmentReflection,
      importingContext: KotlinProjectModelImportingContext
    ): KotlinGradleFragmentProto? {
        val fragmentName = origin.fragmentName ?: return null

        //TODO is it always not null?
        val moduleIdentifier = origin.containingModule?.moduleIdentifier ?: return null
        val kotlinModuleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(moduleIdentifier) ?: return null
        val isTestModule = origin.containingModule?.name == TEST_MODULE_NAME

        // If fragment is already computed, just extract it from context
        importingContext.fragmentStubsByModuleId[kotlinModuleIdentifier]
            ?.find { it.fragmentName == origin.fragmentName }?.also { return it }

        val sourceDirs = origin.kotlinSourceSourceRoots?.srcDirs ?: emptySet()

        //TODO replace with a proper way to compute resources
        val resourceDirs: Set<File> = setOfNotNull(sourceDirs.singleOrNull()?.parentFile?.resolve("resources"))

        val kotlinLanguageSettings = origin.languageSettings?.let { KotlinLanguageSettingsBuilder.buildComponent(it) }

        val directRefinesDependencies = origin.directRefinesDependencies.orEmpty().mapNotNull { dep ->
            buildComponent(dep, importingContext)
        }

        val resolvedDependencies = KotlinFragmentDependencyResolutionBuilder.buildComponent(origin, importingContext)

        return KotlinGradleFragmentProto(
            containingModuleIdentifier = kotlinModuleIdentifier,
            isTestFragment = isTestModule,
            fragmentName = fragmentName,
            languageSettings = kotlinLanguageSettings,
            directRefinesDependencies = directRefinesDependencies,
            sourceDirs = sourceDirs,
            resourceDirs = resourceDirs,
            resolvedDependencies = resolvedDependencies
        ).also {
            importingContext.initializeFragmentProto(it, origin)
        }
    }
}
