// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess

object LinkedProjectPathsTestsFeature : TestFeature<LinkedProjectPaths> {
    override fun renderConfiguration(configuration: LinkedProjectPaths): List<String> = emptyList()
    override fun createDefaultConfiguration(): LinkedProjectPaths = LinkedProjectPaths(mutableSetOf())
}

class LinkedProjectPaths(val linkedProjectPaths: MutableSet<String>)

interface GradleProjectsLinkingDsl {
    fun TestConfigurationDslScope.linkProject(projectPath: String) {
        writeAccess.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.add(projectPath)
    }
}
