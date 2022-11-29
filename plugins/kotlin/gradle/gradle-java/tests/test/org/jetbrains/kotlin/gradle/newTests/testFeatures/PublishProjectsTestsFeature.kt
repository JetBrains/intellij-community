// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.TestFeature
import org.jetbrains.kotlin.gradle.newTests.writeAccess

object GradleProjectsPublishingTestsFeature : TestFeature<ProjectsToPublish> {
    override fun renderConfiguration(configuration: ProjectsToPublish): List<String> = emptyList()

    override fun createDefaultConfiguration(): ProjectsToPublish = ProjectsToPublish(mutableSetOf())
}

class ProjectsToPublish(val publishedSubprojectNames: MutableSet<String>)

interface GradleProjectsPublishingDsl {
    fun TestConfigurationDslScope.publish(vararg subprojectNames: String) {
        writeAccess.getConfiguration(GradleProjectsPublishingTestsFeature)
            .publishedSubprojectNames.addAll(subprojectNames)
    }
}
