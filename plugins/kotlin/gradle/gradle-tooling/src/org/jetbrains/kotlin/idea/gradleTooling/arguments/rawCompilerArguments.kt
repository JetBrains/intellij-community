// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.KotlinRawCompilerArgument

object KotlinRawEmptyCompilerArgument : KotlinRawCompilerArgument<Unit> {
    override val data: Unit
        get() = throw UnsupportedOperationException("Accessing to empty argument data is not allowed!")
}

data class KotlinRawRegularCompilerArgument(override val data: String) : KotlinRawCompilerArgument<String>
data class KotlinRawBooleanCompilerArgument(override val data: Boolean) : KotlinRawCompilerArgument<Boolean>
data class KotlinRawMultipleCompilerArgument(override val data: List<String>) : KotlinRawCompilerArgument<List<String>>

