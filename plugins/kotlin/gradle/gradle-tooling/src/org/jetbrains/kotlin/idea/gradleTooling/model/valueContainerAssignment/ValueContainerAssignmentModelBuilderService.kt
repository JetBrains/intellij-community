// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleTooling.model.valueContainerAssignment

import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModelBuilderService
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModelImpl

interface ValueContainerAssignmentModel : AnnotationBasedPluginModel {
    override fun dump(): DumpedPluginModel {
        return DumpedPluginModelImpl(ValueContainerAssignmentModelImpl::class.java, annotations.toList())
    }
}

class ValueContainerAssignmentModelImpl(
    override val annotations: List<String>
) : ValueContainerAssignmentModel {
    override val presets: List<String>
        get() = emptyList()
}

class ValueContainerAssignmentModelBuilderService : AnnotationBasedPluginModelBuilderService<ValueContainerAssignmentModel>() {
    override val gradlePluginNames
        get() = listOf(
            "org.jetbrains.kotlin.plugin.value.container.assignment",
            "kotlin-value-container-assignment"
        )
    override val extensionName get() = "valueContainerAssignment"
    override val modelClass get() = ValueContainerAssignmentModel::class.java

    override fun createModel(annotations: List<String>, presets: List<String>, extension: Any?): ValueContainerAssignmentModelImpl {
        return ValueContainerAssignmentModelImpl(annotations)
    }
}