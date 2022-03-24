// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinIdeFragmentDependencyResolverReflection

data class KotlinProjectModelImportingContext(
    val project: Project,
    val classLoader: ClassLoader,
    val fragmentCache: KotlinFragmentCache = KotlinFragmentCache.None,
    val fragmentDependencyResolver: KotlinIdeFragmentDependencyResolverReflection? =
        KotlinIdeFragmentDependencyResolverReflection.newInstance(project, classLoader)
)

internal fun KotlinProjectModelImportingContext.withFragmentCache() = copy(fragmentCache = DefaultKotlinFragmentCache())
