// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinFragmentImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.KotlinVariantImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinFragmentReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinVariantReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinFragment
import org.jetbrains.kotlin.idea.projectModel.KotlinModule.Companion.TEST_MODULE_NAME
import java.io.File

internal object KotlinGradleFragmentBuilder : KotlinProjectModelComponentBuilder<KotlinFragmentReflection, KotlinFragment> {
    override fun buildComponent(
        origin: KotlinFragmentReflection,
        importingContext: KotlinProjectModelImportingContext
    ): KotlinFragment? {
        val fragmentName = origin.fragmentName ?: return null
        val moduleIdentifier = origin.containingModule?.moduleIdentifier ?: return null
        val kotlinModuleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(moduleIdentifier) ?: return null

        return importingContext.fragmentCache.withCache(kotlinModuleIdentifier, fragmentName) {
            val isTestModule = origin.containingModule?.name == TEST_MODULE_NAME
            val sourceDirs = origin.kotlinSourceSourceRoots?.srcDirs ?: emptySet()

            //TODO replace with a proper way to compute resources
            val resourceDirs: Set<File> = setOfNotNull(sourceDirs.singleOrNull()?.parentFile?.resolve("resources"))

            val kotlinLanguageSettings = origin.languageSettings?.let { KotlinLanguageSettingsBuilder.buildComponent(it) }

            val directRefinesDependencies = origin.directRefinesDependencies.orEmpty()
                .mapNotNull { dep -> buildComponent(dep, importingContext) }

            val resolvedDependencies = KotlinFragmentDependencyResolutionBuilder.buildComponent(origin, importingContext)

            val fragment = KotlinFragmentImpl(
                fragmentName = fragmentName,
                isTestFragment = isTestModule,
                moduleIdentifier = kotlinModuleIdentifier,
                languageSettings = kotlinLanguageSettings,
                directRefinesFragments = directRefinesDependencies,
                sourceDirs = sourceDirs,
                resourceDirs = resourceDirs,
                resolvedDependencies = resolvedDependencies
            )

            if (origin is KotlinVariantReflection) {
                val variantAttributes = origin.variantAttributes.orEmpty()
                val compilationOutputs = origin.compilationOutputs?.let { KotlinCompilationOutputBuilder.buildComponent(it) }

                return@withCache KotlinVariantImpl(
                    fragment,
                    variantAttributes = variantAttributes,
                    compilationOutputs = compilationOutputs
                )
            }

            return@withCache fragment
        }
    }
}
