// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.File
import java.io.Serializable

interface KonanArtifactModel : Serializable {
    val targetName: String // represents org.jetbrains.kotlin.gradle.plugin.KotlinTarget.name, ex: "iosX64", "iosArm64"
    val executableName: String // a base name for the output binary file
    val type: String // represents org.jetbrains.kotlin.konan.target.CompilerOutputKind.name, ex: "PROGRAM", "FRAMEWORK"
    val targetPlatform: String // represents org.jetbrains.kotlin.konan.target.KonanTarget.name
    val file: File // the output binary file
    val buildTaskPath: String
    val runConfiguration: KonanRunConfigurationModel
    val isTests: Boolean
    val freeCompilerArgs: Array<String>?
    val exportDependencies: Array<KotlinDependencyId>?
    val binaryOptions: Array<String>?
}
