// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationOutputImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilationOutputReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput

object KotlinCompilationOutputBuilder : KotlinModelComponentBuilderBase<KotlinCompilationOutputReflection, KotlinCompilationOutput> {
    override fun buildComponent(origin: KotlinCompilationOutputReflection): KotlinCompilationOutput? {
        return KotlinCompilationOutputImpl(
            classesDirs = origin.classesDirs?.toSet() ?: return null,
            resourcesDir = origin.resourcesDir,
            effectiveClassesDir = null,
        )
    }
}
