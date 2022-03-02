// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import java.io.Serializable

interface KotlinProjectModelSettings : Serializable {
    val coreLibrariesVersion: String
    val explicitApiModeCliOption: String?
}

interface KotlinKPMGradleModel : Serializable {
    val kpmModules: Collection<KotlinModule>
    val kotlinNativeHome: String
    val settings: KotlinProjectModelSettings
}