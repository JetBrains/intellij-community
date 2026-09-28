// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import junit.framework.TestCase

class KotlinBuildToolsImplLocationTest : TestCase() {

    fun `test the implementation directory name uses the maven artifact version`() {
        assertEquals(
            "kotlin-build-tools-impl-2.4.10",
            KotlinArtifactsDownloader.getBuildToolsImplDirectory("2.4.10-release-377").fileName.toString(),
        )
        assertEquals(
            "kotlin-build-tools-impl-2.5.0-dev-4967",
            KotlinArtifactsDownloader.getBuildToolsImplDirectory("2.5.0-dev-4967").fileName.toString(),
        )
    }

    fun `test the implementation is available since the first supported line`() {
        assertFalse(KotlinArtifactsDownloader.isBuildToolsImplSupported("2.5.10"))
        assertTrue(KotlinArtifactsDownloader.isBuildToolsImplSupported("2.5.20"))
        assertTrue(KotlinArtifactsDownloader.isBuildToolsImplSupported("2.5.20-Beta1"))
    }
}
