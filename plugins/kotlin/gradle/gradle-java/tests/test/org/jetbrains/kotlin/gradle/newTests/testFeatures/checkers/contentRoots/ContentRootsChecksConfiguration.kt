// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots

class ContentRootsChecksConfiguration {
    var hideTestSourceRoots: Boolean = false
    var hideResourceRoots: Boolean = false

    // always hidden for now
    val hideAndroidSpecificRoots: Boolean = true
    val hideGeneratedRoots: Boolean = true
}
