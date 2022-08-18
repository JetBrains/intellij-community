// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling.model.allopen

import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModelBuilderService
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModel
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.DumpedPluginModelImpl


interface AllOpenModel : AnnotationBasedPluginModel {
    override fun dump(): DumpedPluginModel {
        return DumpedPluginModelImpl(AllOpenModelImpl::class.java, annotations.toList(), presets.toList())
    }
}

class AllOpenModelImpl(
    override val annotations: List<String>,
    override val presets: List<String>
) : AllOpenModel

class AllOpenModelBuilderService : AnnotationBasedPluginModelBuilderService<AllOpenModel>() {
    override val gradlePluginNames get() = listOf("org.jetbrains.kotlin.plugin.allopen", "kotlin-allopen")
    override val extensionName get() = "allOpen"
    override val modelClass get() = AllOpenModel::class.java

    override fun createModel(annotations: List<String>, presets: List<String>, extension: Any?): AllOpenModelImpl {
        return AllOpenModelImpl(annotations, presets)
    }
}