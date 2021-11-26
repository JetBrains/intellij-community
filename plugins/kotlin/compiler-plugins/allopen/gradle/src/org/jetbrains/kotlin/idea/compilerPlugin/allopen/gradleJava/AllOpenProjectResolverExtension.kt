// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen.gradleJava

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.gradleTooling.model.allopen.AllOpenModel
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AnnotationBasedPluginProjectResolverExtension

class AllOpenProjectResolverExtension : AnnotationBasedPluginProjectResolverExtension<AllOpenModel>() {
    companion object {
        val KEY = Key<AllOpenModel>("AllOpenModel")
    }

    override val modelClass get() = AllOpenModel::class.java
    override val userDataKey get() = KEY
}