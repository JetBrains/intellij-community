/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.jetbrains.kotlin.gradle.KotlinCompilationOutput
import org.jetbrains.kotlin.gradle.KotlinCompilationOutputImpl
import org.jetbrains.kotlin.reflect.KotlinCompilationOutputReflection

object KotlinCompilationOutputBuilder : KotlinModelComponentBuilderBase<KotlinCompilationOutputReflection, KotlinCompilationOutput> {
    override fun buildComponent(origin: KotlinCompilationOutputReflection): KotlinCompilationOutput? {
        return KotlinCompilationOutputImpl(
            classesDirs = origin.classesDirs?.toSet() ?: return null,
            resourcesDir = origin.resourcesDir,
            effectiveClassesDir = null,
        )
    }
}
