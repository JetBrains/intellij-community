// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelSettings
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelSettingsImpl
import org.jetbrains.kotlin.idea.gradleTooling.get

object KotlinProjectModelSettingsBuilder : KotlinModelComponentBuilderBase<KotlinProjectModelSettings> {
    override fun buildComponent(origin: Any): KotlinProjectModelSettings? {
        val coreLibrariesVersion = (origin["getCoreLibrariesVersion"] as? String) ?: return null
        val explicitApi = origin["getExplicitApi"]
        val cliOption = explicitApi?.let { it["getCliOption"] as? String }
        return KotlinProjectModelSettingsImpl(coreLibrariesVersion, cliOption)
    }
}