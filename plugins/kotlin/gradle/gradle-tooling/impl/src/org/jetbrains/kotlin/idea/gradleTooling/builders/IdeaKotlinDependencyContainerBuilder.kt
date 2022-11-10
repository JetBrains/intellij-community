// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinSerializedDependenciesContainer
import org.jetbrains.kotlin.idea.gradleTooling.MultiplatformModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.kotlin.idea.gradleTooling.useKgpDependencyResolution

fun buildIdeaKotlinDependenciesContainer(
    context: MultiplatformModelImportingContext,
    extension: KotlinExtensionReflection
): IdeaKotlinSerializedDependenciesContainer? {
    if (!context.useKgpDependencyResolution()) return null

    val importReflection = context.importReflection ?: return null
    return IdeaKotlinSerializedDependenciesContainer(extension.sourceSets.associate { sourceSet ->
        sourceSet.name to importReflection.resolveDependenciesSerialized(sourceSet).toList()
    })
}
