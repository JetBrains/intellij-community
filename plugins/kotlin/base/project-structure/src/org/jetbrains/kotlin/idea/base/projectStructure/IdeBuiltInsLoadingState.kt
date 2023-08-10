// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IdeBuiltInsLoadingState {
    val state: IdeBuiltInsLoading = IdeBuiltInsLoading.FROM_DEPENDENCIES_JVM

    enum class IdeBuiltInsLoading {
        FROM_CLASSLOADER,
        FROM_DEPENDENCIES_JVM;
    }

    val isFromDependenciesForJvm: Boolean
        get() = state == IdeBuiltInsLoading.FROM_DEPENDENCIES_JVM

    val isFromClassLoader: Boolean
        get() = state == IdeBuiltInsLoading.FROM_CLASSLOADER
}