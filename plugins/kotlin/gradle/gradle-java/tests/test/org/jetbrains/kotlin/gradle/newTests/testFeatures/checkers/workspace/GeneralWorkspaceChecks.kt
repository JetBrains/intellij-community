// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.workspace

import org.jetbrains.kotlin.gradle.newTests.TestFeature

// Architectural oddity: used directly by [WorkspaceModelChecker], doesn't need to be installed explicitly
// because all specific checkers inherit [WorkspaceModelChecker]
object GeneralWorkspaceChecks : TestFeature<GeneralWorkspaceChecksConfiguration> {
    override fun createDefaultConfiguration(): GeneralWorkspaceChecksConfiguration = GeneralWorkspaceChecksConfiguration()
}
