// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKotlinProjectModel
import org.jetbrains.plugins.gradle.tooling.serialization.DefaultSerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService

class IdeaKotlinProjectModelSerializationService : SerializationService<IdeaKotlinProjectModel> {
    override fun getModelClass(): Class<out IdeaKotlinProjectModel> = IdeaKotlinProjectModel::class.java

    override fun write(`object`: IdeaKotlinProjectModel?, modelClazz: Class<out IdeaKotlinProjectModel>?): ByteArray =
        DefaultSerializationService().write(`object`, modelClazz)

    override fun read(`object`: ByteArray?, modelClazz: Class<out IdeaKotlinProjectModel>?): IdeaKotlinProjectModel? =
        DefaultSerializationService().read(`object`, modelClazz) as? IdeaKotlinProjectModel
}