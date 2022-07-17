// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinCompilerArgument<T> : Serializable {
    val data: T
}

interface KotlinRawCompilerArgument<TRaw> : KotlinCompilerArgument<TRaw>

interface KotlinCachedCompilerArgument<CacheType> : KotlinCompilerArgument<CacheType>
