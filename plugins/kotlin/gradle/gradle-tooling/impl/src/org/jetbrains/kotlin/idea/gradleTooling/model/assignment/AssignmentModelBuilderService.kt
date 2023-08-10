/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.gradleTooling.model.assignment

import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModelBuilderService
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModelImpl

interface AssignmentModel : AnnotationBasedPluginModel {
    override fun dump(): DumpedPluginModel {
        return DumpedPluginModelImpl(AssignmentModelImpl::class.java, annotations.toList())
    }
}

class AssignmentModelImpl(
    override val annotations: List<String>
) : AssignmentModel {
    override val presets: List<String>
        get() = emptyList()
}

class AssignmentModelBuilderService : AnnotationBasedPluginModelBuilderService<AssignmentModel>() {
    override val gradlePluginNames
        get() = listOf(
            "org.jetbrains.kotlin.plugin.assignment",
            "kotlin-assignment"
        )
    override val extensionName get() = "assignment"
    override val modelClass get() = AssignmentModel::class.java

    override fun createModel(annotations: List<String>, presets: List<String>, extension: Any?): AssignmentModelImpl {
        return AssignmentModelImpl(annotations)
    }
}