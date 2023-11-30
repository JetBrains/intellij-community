// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinFacetFieldsUtils")

package org.jetbrains.kotlin.idea.facet

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind

fun getExposedFacetFields(platformKind: IdePlatformKind): List<String> {
    val fields = when (platformKind) {
        is JvmIdePlatformKind -> jvmFields
        is JsIdePlatformKind -> jsFields
        is CommonIdePlatformKind -> metadataFields
        else -> commonFields
    }
    return fields.exposedFields
}

internal class KotlinFacetFields(
    base: KotlinFacetFields? = null,
    exposedFields: List<String>,
    hiddenFields: List<String>
) {
    val exposedFields: List<String> = if (base != null) base.exposedFields + exposedFields else exposedFields
    private val hiddenFields: List<String> = if (base != null) base.hiddenFields + hiddenFields else hiddenFields
    val allFields: List<String>
        get() = exposedFields + hiddenFields
}

internal val commonFields = KotlinFacetFields(
    exposedFields = listOf(
        CommonCompilerArguments::languageVersion.name,
        CommonCompilerArguments::apiVersion.name,
        CommonCompilerArguments::suppressWarnings.name
    ),
    hiddenFields = listOf(
        CommonCompilerArguments::pluginClasspaths.name,
        CommonCompilerArguments::pluginOptions.name,
        CommonCompilerArguments::pluginConfigurations.name,
        CommonCompilerArguments::multiPlatform.name
    )
)

internal val jvmFields = KotlinFacetFields(
    base = commonFields,
    exposedFields = listOf(
        K2JVMCompilerArguments::jvmTarget.name,
        K2JVMCompilerArguments::destination.name,
        K2JVMCompilerArguments::classpath.name,
    ),
    hiddenFields = listOf(
        K2JVMCompilerArguments::friendPaths.name,
    )
)

internal val jsFields = KotlinFacetFields(
    base = commonFields,
    exposedFields = listOf(
        K2JSCompilerArguments::sourceMap.name,
        K2JSCompilerArguments::sourceMapPrefix.name,
        K2JSCompilerArguments::sourceMapEmbedSources.name,
        K2JSCompilerArguments::moduleKind.name
    ),
    hiddenFields = emptyList()
)

internal val metadataFields = KotlinFacetFields(
    base = commonFields,
    exposedFields = listOf(
        K2MetadataCompilerArguments::destination.name,
        K2MetadataCompilerArguments::classpath.name
    ),
    hiddenFields = emptyList()
)

internal val CommonCompilerArguments.kotlinFacetFields: KotlinFacetFields
    get() = when (this) {
        is K2JVMCompilerArguments -> jvmFields
        is K2JSCompilerArguments -> jsFields
        is K2MetadataCompilerArguments -> metadataFields
        else -> commonFields
    }
