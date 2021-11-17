// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationOutputImpl
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput
import java.io.File

object KotlinCompilationOutputBuilder : KotlinModelComponentBuilderBase<KotlinCompilationOutput>{
    override fun buildComponent(origin: Any): KotlinCompilationOutput? {
        val gradleOutputClass = origin.javaClass
        val getClassesDirs = gradleOutputClass.getMethodOrNull("getClassesDirs") ?: return null
        val getResourcesDir = gradleOutputClass.getMethodOrNull("getResourcesDir") ?: return null
        val classesDirs = getClassesDirs(origin) as? FileCollection ?: return null
        val resourcesDir = getResourcesDir(origin) as? File ?: return null
        return KotlinCompilationOutputImpl(classesDirs.files, null, resourcesDir)
    }
}