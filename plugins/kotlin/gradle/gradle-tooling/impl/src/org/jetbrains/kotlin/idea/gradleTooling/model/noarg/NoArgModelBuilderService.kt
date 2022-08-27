// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling.model.noarg

import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModelBuilderService
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModelImpl

interface NoArgModel : AnnotationBasedPluginModel {
    val invokeInitializers: Boolean

    override fun dump(): DumpedPluginModel {
        return DumpedPluginModelImpl(NoArgModelImpl::class.java, annotations.toList(), presets.toList(), invokeInitializers)
    }
}

class NoArgModelImpl(
    override val annotations: List<String>,
    override val presets: List<String>,
    override val invokeInitializers: Boolean
) : NoArgModel

class NoArgModelBuilderService : AnnotationBasedPluginModelBuilderService<NoArgModel>() {
    override val gradlePluginNames get() = listOf("org.jetbrains.kotlin.plugin.noarg", "kotlin-noarg")
    override val extensionName get() = "noArg"
    override val modelClass get() = NoArgModel::class.java

    override fun createModel(annotations: List<String>, presets: List<String>, extension: Any?): NoArgModel {
        val invokeInitializers = extension?.getFieldValue("invokeInitializers") as? Boolean ?: false
        return NoArgModelImpl(annotations, presets, invokeInitializers)
    }
}