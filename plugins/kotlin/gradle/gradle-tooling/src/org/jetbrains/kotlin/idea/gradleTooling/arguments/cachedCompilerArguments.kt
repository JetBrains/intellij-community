// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument

object KotlinCachedEmptyCompilerArgument : KotlinCachedCompilerArgument<Unit> {
    override val data: Unit
        get() = throw UnsupportedOperationException("Accessing to empty argument data is not allowed!")
}

data class KotlinCachedBooleanCompilerArgument(override val data: Int) : KotlinCachedCompilerArgument<Int>
data class KotlinCachedRegularCompilerArgument(override val data: Int) : KotlinCachedCompilerArgument<Int>

data class KotlinCachedMultipleCompilerArgument(override val data: List<KotlinCachedCompilerArgument<*>>) :
    KotlinCachedCompilerArgument<List<KotlinCachedCompilerArgument<*>>>
