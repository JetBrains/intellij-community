// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinFragmentResolvedDependency
import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object KotlinFragmentDependencyResolutionBuilder : KotlinProjectModelComponentBuilder<Collection<KotlinFragmentResolvedDependency>> {
    private var isInitialized: Boolean = false
    private lateinit var chooseVisibleSourceSetsClass: Class<*>
    private lateinit var keepOriginalDependencyClass: Class<*>
    private lateinit var projectMetadataProviderClass: Class<*>

    override fun buildComponent(
        origin: Any,
        importingContext: KotlinProjectModelImportingContext
    ): Collection<KotlinFragmentResolvedDependency> {
        initializeIfNeeded(importingContext)
        val dependencyClass = origin.javaClass
        val dependencyProperty = origin["getDependency"] as ResolvedComponentResult
        return when (val dependencyId = dependencyProperty.id) {
            is ModuleComponentIdentifier -> when {
                chooseVisibleSourceSetsClass.isAssignableFrom(dependencyClass) -> {
                    val metadataProvider = origin.metadataProvider
                    return origin.visibleSourceSetsNames.map {
                        val identifier = "${dependencyId.group}:${dependencyId.module}:$it:${dependencyId.version}"
                        val files = (metadataProvider["getSourceSetCompiledMetadata", it] as FileCollection).files
                        KotlinFragmentResolvedBinaryDependency(identifier, files)
                    }
                }
                keepOriginalDependencyClass.isAssignableFrom(dependencyClass) -> listOf(KotlinFragmentResolvedBinaryDependency(dependencyId.displayName))
                else -> emptyList()
            }

            is ProjectComponentIdentifier -> when {
                chooseVisibleSourceSetsClass.isAssignableFrom(dependencyClass) -> {
                    val metadataProvider = origin.metadataProvider
                    val moduleIdentifier = metadataProvider.javaClass.kotlin.memberProperties.single { it.name == "moduleIdentifier" }
                        .apply { isAccessible = true }
                        .get(metadataProvider)
                    val moduleName = (moduleIdentifier["moduleClassifier"] as String?) ?: KotlinModule.MAIN_MODULE_NAME
                    val dependencyIdPrefix = dependencyId.projectPath.takeIf { it.isNotEmpty() && it != ":" }
                        ?: dependencyId.projectName
                    return origin.visibleSourceSetsNames.map {
                        val identifier = "$dependencyIdPrefix:${it + moduleName.replaceFirstChar { it.uppercaseChar() }}"
                        KotlinFragmentResolvedSourceDependency(identifier)
                    }
                }
                else -> emptyList()
            }
            else -> error("Unsupported dependency with type '${dependencyId.javaClass.name}'")
        }
    }

    private fun initializeIfNeeded(importingContext: KotlinProjectModelImportingContext) {
        if (!isInitialized) {
            chooseVisibleSourceSetsClass = importingContext.classLoader.loadClass(CHOOSE_VISIBLE_SOURCE_SETS_CLASS)
            keepOriginalDependencyClass = importingContext.classLoader.loadClass(KEEP_ORIGINAL_DEPENDENCY_CLASS)
            projectMetadataProviderClass = importingContext.classLoader.loadClass(PROJECT_METADATA_PROVIDER_CLASS)
        }
    }

    private val Any.metadataProvider: Any
        get() {
            val metadataProvider = this["getMetadataProvider"] ?: this["getMetadataProvider\$kotlin_gradle_plugin"]
            val metadataProviderClass = metadataProvider!!.javaClass
            require(projectMetadataProviderClass.isAssignableFrom(metadataProviderClass)) {
                "Cannot resolve $CHOOSE_VISIBLE_SOURCE_SETS_CLASS dependency with provider '$metadataProviderClass'. '$KEEP_ORIGINAL_DEPENDENCY_CLASS' is required!"
            }
            return metadataProvider
        }

    @Suppress("UNCHECKED_CAST")
    private val Any.visibleSourceSetsNames: Collection<String>
        get() = this["getAllVisibleSourceSetNames"] as Collection<String>

    private const val CHOOSE_VISIBLE_SOURCE_SETS_CLASS =
        "org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution\$ChooseVisibleSourceSets"

    private const val KEEP_ORIGINAL_DEPENDENCY_CLASS =
        "org.jetbrains.kotlin.gradle.plugin.mpp.MetadataDependencyResolution\$KeepOriginalDependency"

    private const val PROJECT_METADATA_PROVIDER_CLASS =
        "org.jetbrains.kotlin.gradle.plugin.mpp.ProjectMetadataProviderImpl"
}