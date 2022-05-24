// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKpmProject
import org.jetbrains.plugins.gradle.tooling.serialization.DefaultSerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService

class IdeaKpmProjectSerializationService : SerializationService<IdeaKpmProject> {

    // TODO Yaroslav Chernyshev
    //  `test 'runPartialGradleImport' is running in 'lenient' or 'classpath' mode` breaks on KGP 1.6.21 with NoClassDefFoundError
    override fun getModelClass(): Class<out IdeaKpmProject> = try {
        IdeaKpmProject::class.java
    } catch (t: NoClassDefFoundError) {
        Nothing::class.java
    }

    override fun write(`object`: IdeaKpmProject?, modelClazz: Class<out IdeaKpmProject>?): ByteArray =
        DefaultSerializationService().write(`object`, modelClazz)

    override fun read(`object`: ByteArray?, modelClazz: Class<out IdeaKpmProject>?): IdeaKpmProject? =
        DefaultSerializationService().read(`object`, modelClazz) as? IdeaKpmProject
}
