// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinAndroidSourceSetInfo
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import kotlin.test.fail

fun KotlinMPPGradleModel.getSourceSetOrFail(sourceSetName: String): KotlinSourceSet {
    return sourceSetsByName[sourceSetName] ?: fail(
        "Missing KotlinSourceSet: $sourceSetName. All known SourceSets: ${sourceSetsByName.keys}"
    )
}

fun KotlinMPPGradleModel.getKotlinGradlePluginVersionOrFail() : KotlinGradlePluginVersion {
    return kotlinGradlePluginVersion ?: fail("Missing 'kotlinGradlePluginVersion'")
}

fun KotlinSourceSet.assertNoAndroidSourceSetInfo() {
    kotlin.test.assertNull(androidSourceSetInfo, "Expected no 'androidSourceSetInfo' for $name")
}

fun KotlinSourceSet.getAndroidSourceSetInfoOrFail(): KotlinAndroidSourceSetInfo {
    return kotlin.test.assertNotNull(androidSourceSetInfo, "Missing 'androidSourceSetInfo' for $name")
}