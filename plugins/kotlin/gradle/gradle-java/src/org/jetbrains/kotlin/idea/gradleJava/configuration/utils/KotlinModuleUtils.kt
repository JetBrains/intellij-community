// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.utils

import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleTooling.compilationFullName
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun KotlinComponent.fullName(simpleName: String = name) = when (this) {
    is KotlinCompilation -> compilationFullName(simpleName, disambiguationClassifier)
    else -> simpleName
}

fun KotlinMPPGradleProjectResolver.Companion.getKotlinModuleId(
    gradleModule: IdeaModule, kotlinComponent: KotlinComponent, resolverCtx: ProjectResolverContext
) = getGradleModuleQualifiedName(resolverCtx, gradleModule, kotlinComponent.fullName())