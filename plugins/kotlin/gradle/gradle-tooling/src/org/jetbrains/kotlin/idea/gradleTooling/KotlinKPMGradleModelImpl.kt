// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.*
import java.io.File

class KotlinProjectModelSettingsImpl(
    override val coreLibrariesVersion: String,
    override val explicitApiModeCliOption: String?,
) : KotlinProjectModelSettings

data class KotlinLocalModuleIdentifierImpl(
    override val moduleClassifier: String?,
    override val buildId: String,
    override val projectId: String
) : KotlinLocalModuleIdentifier

data class KotlinMavenModuleIdentifierImpl(
    override val moduleClassifier: String?,
    override val group: String,
    override val name: String
) : KotlinMavenModuleIdentifier

class KotlinFragmentResolvedSourceDependency(override val dependencyIdentifier: String) : KotlinFragmentResolvedDependency

class KotlinFragmentResolvedBinaryDependency(override val dependencyIdentifier: String, val dependencyContent: Set<File>? = null) :
    KotlinFragmentResolvedDependency

data class KotlinGradleFragmentProto(
    val containingModuleIdentifier: KotlinModuleIdentifier,
    val isTestFragment: Boolean,
    val fragmentName: String,
    val languageSettings: KotlinLanguageSettings?,
    val directRefinesDependencies: Collection<KotlinGradleFragmentProto>,
    val sourceDirs: Set<File>,
    val resourceDirs: Set<File>
)

fun KotlinGradleFragmentProto.buildKotlinFragment(
    refinesKotlinFragments: Collection<KotlinFragment>,
    resolvedModuleDependencies: Collection<KotlinFragmentResolvedDependency>
): KotlinFragment = KotlinGradleFragment(
    fragmentName = fragmentName,
    isTestFragment = isTestFragment,
    moduleIdentifier = containingModuleIdentifier,
    languageSettings = languageSettings,
    directRefinesFragments = refinesKotlinFragments,
    resolvedDependencies = resolvedModuleDependencies,
    sourceDirs = sourceDirs,
    resourceDirs = resourceDirs
)

class KotlinGradleFragment(
    override val fragmentName: String,
    override val isTestFragment: Boolean,
    override val moduleIdentifier: KotlinModuleIdentifier,
    override val languageSettings: KotlinLanguageSettings?,
    override val directRefinesFragments: Collection<KotlinFragment>,
    override val resolvedDependencies: Collection<KotlinFragmentResolvedDependency>,
    override val sourceDirs: Set<File>,
    override val resourceDirs: Set<File>
) : KotlinFragment

class KotlinGradleVariantData(
    override val variantAttributes: KotlinKPMVariantAttributesMap,
    override val compilationOutputs: KotlinCompilationOutput?
) : KotlinVariantData

class KotlinGradleModule(
    override val moduleIdentifier: KotlinModuleIdentifier,
    override var fragments: Collection<KotlinFragment>,
    override var variants: Collection<KotlinVariant>
) : KotlinModule


class KotlinKPMGradleModelImpl(
    override val kpmModules: Collection<KotlinModule>,
    override val settings: KotlinProjectModelSettings,
    override val kotlinNativeHome: String,
) : KotlinKPMGradleModel
