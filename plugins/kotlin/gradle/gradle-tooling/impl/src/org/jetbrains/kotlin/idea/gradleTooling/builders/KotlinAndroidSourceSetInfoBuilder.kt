// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.KotlinAndroidSourceSetInfoImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinAndroidSourceSetInfoReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinAndroidSourceSetInfo

object KotlinAndroidSourceSetInfoBuilder {

    private val logger = Logging.getLogger(javaClass)

    internal fun buildKotlinAndroidSourceSetInfo(
        info: KotlinAndroidSourceSetInfoReflection,
    ): KotlinAndroidSourceSetInfo? {
        return KotlinAndroidSourceSetInfoImpl(
            kotlinSourceSetName = info.kotlinSourceSetName ?: return null,
            androidSourceSetName = info.androidSourceSetName ?: return null,
            androidVariantNames = info.androidVariantNames ?: return null
        )
    }

}