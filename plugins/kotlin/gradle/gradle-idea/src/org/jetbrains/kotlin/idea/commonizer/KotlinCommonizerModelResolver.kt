// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.commonizer

import org.jetbrains.kotlin.gradle.EnableCommonizerTask
import org.jetbrains.plugins.gradle.model.ClassSetProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KotlinCommonizerModelResolver : AbstractProjectResolverExtension() {
    override fun getProjectsLoadedModelProvider() = ClassSetProjectImportModelProvider(
        setOf(EnableCommonizerTask::class.java)
    )
}


