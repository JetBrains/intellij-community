/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.gradle

import java.io.File
import java.io.Serializable

interface KotlinFragmentResolvedDependency : Serializable {
    val dependencyIdentifier: String
}

interface KotlinFragment : Serializable {
    val fragmentName: String
    val isTestFragment: Boolean
    val moduleIdentifier: KotlinModuleIdentifier
    val languageSettings: KotlinLanguageSettings?
    val directRefinesFragments: Collection<KotlinFragment>
    val resolvedDependencies: Collection<KotlinFragmentResolvedDependency>
    val sourceDirs: Set<File>
    val resourceDirs: Set<File>
}

typealias KotlinKPMAttributeKeyId = String
typealias KotlinKPMAttributeValueId = String
typealias KotlinKPMVariantAttributesMap = Map<KotlinKPMAttributeKeyId, KotlinKPMAttributeValueId>

interface KotlinVariant : KotlinFragment {
    val variantAttributes: KotlinKPMVariantAttributesMap
    val compilationOutputs: KotlinCompilationOutput?
}

interface KotlinModuleIdentifier : Serializable {
    val moduleClassifier: String?
}

interface KotlinLocalModuleIdentifier : KotlinModuleIdentifier {
    val buildId: String
    val projectId: String
}

interface KotlinMavenModuleIdentifier : KotlinModuleIdentifier {
    val group: String
    val name: String
}

interface KotlinKPMModule : Serializable {
    val moduleIdentifier: KotlinModuleIdentifier
    val fragments: Collection<KotlinFragment>

    companion object {
        const val MAIN_MODULE_NAME = "main"
        const val TEST_MODULE_NAME = "test"
    }
}

val KotlinKPMModule.variants: Collection<KotlinVariant>
    get() = fragments.filterIsInstance<KotlinVariant>()

interface KotlinProjectModelSettings : Serializable {
    val coreLibrariesVersion: String
    val explicitApiModeCliOption: String?
}

interface KotlinKPMGradleModel : Serializable {
    val kpmModules: Collection<KotlinKPMModule>
    val kotlinNativeHome: String
    val settings: KotlinProjectModelSettings
}