/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

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

class KotlinFragmentImpl(
    override val fragmentName: String,
    override val isTestFragment: Boolean,
    override val moduleIdentifier: KotlinModuleIdentifier,
    override val languageSettings: KotlinLanguageSettings?,
    override val directRefinesFragments: Collection<KotlinFragment>,
    override val resolvedDependencies: Collection<KotlinFragmentResolvedDependency>,
    override val sourceDirs: Set<File>,
    override val resourceDirs: Set<File>
) : KotlinFragment

internal class KotlinVariantImpl(
    private val fragment: KotlinFragment,
    override val variantAttributes: KotlinKPMVariantAttributesMap,
    override val compilationOutputs: KotlinCompilationOutput?
) : KotlinVariant, KotlinFragment by fragment

class KotlinModuleImpl(
    override val moduleIdentifier: KotlinModuleIdentifier,
    override val fragments: Collection<KotlinFragment>,
) : KotlinKPMModule

class KotlinKPMGradleModelImpl(
    override val kpmModules: Collection<KotlinKPMModule>,
    override val settings: KotlinProjectModelSettings,
    override val kotlinNativeHome: String,
) : KotlinKPMGradleModel
