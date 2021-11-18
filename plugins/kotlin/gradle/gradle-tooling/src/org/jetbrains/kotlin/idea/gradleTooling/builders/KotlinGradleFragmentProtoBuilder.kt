// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleFragmentProto
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.gradleTooling.initializeFragmentProto
import org.jetbrains.kotlin.idea.projectModel.KotlinModule.Companion.TEST_MODULE_NAME
import java.io.File

object KotlinGradleFragmentProtoBuilder : KotlinProjectModelComponentBuilder<KotlinGradleFragmentProto> {
    override fun buildComponent(origin: Any, importingContext: KotlinProjectModelImportingContext): KotlinGradleFragmentProto? {
        val fragmentName = origin["getFragmentName"] as? String ?: return null
        val kotlinModule = origin["getContainingModule"] ?: return null

        //TODO is it always not null?
        val moduleIdentifier = kotlinModule["getModuleIdentifier"] ?: return null
        val kotlinModuleIdentifier = KotlinModuleIdentifierBuilder.buildComponent(moduleIdentifier) ?: return null

        val isTestModule = (kotlinModule as? Named)?.name == TEST_MODULE_NAME

        // If fragment is already computed, just extract it from context
        importingContext.fragmentStubsByModuleId[kotlinModuleIdentifier]
            ?.find { it.fragmentName == fragmentName }?.also { return it }


        @Suppress("UNCHECKED_CAST")
        val sourceDirs = (origin["getKotlinSourceRoots"] as? SourceDirectorySet)?.srcDirs ?: emptySet()
        //TODO replace with a proper way to compute resources
        val resourceDirs: Set<File> = setOfNotNull(sourceDirs.singleOrNull()?.parentFile?.resolve("resources"))

        val languageSettings = origin["getLanguageSettings"]
        val kotlinLanguageSettings = languageSettings?.let { KotlinLanguageSettingsBuilder.buildComponent(it) }

        @Suppress("UNCHECKED_CAST")
        val directRefinesDependencies = (origin["getDirectRefinesDependencies"] as? Iterable<Any>)?.mapNotNull { dep ->
            buildComponent(dep, importingContext)
        } ?: emptyList()

        return KotlinGradleFragmentProto(
            containingModuleIdentifier = kotlinModuleIdentifier,
            isTestFragment = isTestModule,
            fragmentName = fragmentName,
            languageSettings = kotlinLanguageSettings,
            directRefinesDependencies = directRefinesDependencies,
            sourceDirs = sourceDirs,
            resourceDirs = resourceDirs,
        ).also {
            importingContext.initializeFragmentProto(it, origin)
        }
    }
}