// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelSettings
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelSettingsImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinKpmExtensionReflection

object KotlinProjectModelSettingsBuilder : KotlinModelComponentBuilderBase<KotlinKpmExtensionReflection, KotlinProjectModelSettings> {
    override fun buildComponent(origin: KotlinKpmExtensionReflection): KotlinProjectModelSettings? {
        return KotlinProjectModelSettingsImpl(
            coreLibrariesVersion = origin.coreLibrariesVersion ?: return null,
            explicitApiModeCliOption = origin.explicitApiCliOption
        )
    }
}
