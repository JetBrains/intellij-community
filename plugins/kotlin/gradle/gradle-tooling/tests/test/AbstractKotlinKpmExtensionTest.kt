// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder

import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinPm20ProjectExtension
import org.junit.Before

typealias KotlinKpmExtension = KotlinPm20ProjectExtension

abstract class AbstractKotlinKpmExtensionTest {
    lateinit var project: ProjectInternal
        private set

    lateinit var kotlin: KotlinKpmExtension

    @Before
    fun setup() {
        project = ProjectBuilder.builder().build() as ProjectInternal
        project.plugins.apply("org.jetbrains.kotlin.multiplatform.pm20")
        kotlin = project.extensions.getByName("kotlin") as KotlinKpmExtension
    }
}
