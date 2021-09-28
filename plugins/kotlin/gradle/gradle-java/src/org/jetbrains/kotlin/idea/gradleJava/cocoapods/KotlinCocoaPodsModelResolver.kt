// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.cocoapods

import org.jetbrains.kotlin.idea.gradleTooling.EnablePodImportTask
import org.jetbrains.plugins.gradle.model.ClassSetProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KotlinCocoaPodsModelResolver : AbstractProjectResolverExtension() {
    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(EnablePodImportTask::class.java)
    }

    override fun getProjectsLoadedModelProvider(): ProjectImportModelProvider {
        return ClassSetProjectImportModelProvider(
            setOf(EnablePodImportTask::class.java)
        )
    }
}
