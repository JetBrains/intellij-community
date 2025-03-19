// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.gradleJava

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AnnotationBasedPluginProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleTooling.model.noarg.NoArgModel

class NoArgProjectResolverExtension : AnnotationBasedPluginProjectResolverExtension<NoArgModel>() {
    companion object {
        val KEY = Key.create(NoArgModel::class.java, 1)
    }

    override val modelClass get() = NoArgModel::class.java
    override val userDataKey get() = KEY
}