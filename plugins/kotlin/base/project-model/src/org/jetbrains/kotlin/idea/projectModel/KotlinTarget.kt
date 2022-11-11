// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinTarget : Serializable {
    val name: String
    val presetName: String?
    val disambiguationClassifier: String?
    val platform: KotlinPlatform
    val compilations: Collection<KotlinCompilation>
    val testRunTasks: Collection<KotlinTestRunTask>
    val nativeMainRunTasks: Collection<KotlinNativeMainRunTask>
    val jar: KotlinTargetJar?
    val konanArtifacts: List<KonanArtifactModel>

    companion object {
        const val METADATA_TARGET_NAME = "metadata"
    }
}
