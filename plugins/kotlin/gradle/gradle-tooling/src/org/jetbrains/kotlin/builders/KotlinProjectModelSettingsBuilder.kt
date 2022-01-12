/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.jetbrains.kotlin.gradle.KotlinProjectModelSettings
import org.jetbrains.kotlin.gradle.KotlinProjectModelSettingsImpl
import org.jetbrains.kotlin.reflect.KotlinKpmExtensionReflection

object KotlinProjectModelSettingsBuilder : KotlinModelComponentBuilderBase<KotlinKpmExtensionReflection, KotlinProjectModelSettings> {
    override fun buildComponent(origin: KotlinKpmExtensionReflection): KotlinProjectModelSettings? {
        return KotlinProjectModelSettingsImpl(
            coreLibrariesVersion = origin.coreLibrariesVersion ?: return null,
            explicitApiModeCliOption = origin.explicitApiCliOption
        )
    }
}
