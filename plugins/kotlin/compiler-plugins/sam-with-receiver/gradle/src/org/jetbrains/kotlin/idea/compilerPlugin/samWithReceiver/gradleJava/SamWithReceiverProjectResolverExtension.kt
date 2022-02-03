// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver.gradleJava

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AnnotationBasedPluginProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleTooling.model.samWithReceiver.SamWithReceiverModel

class SamWithReceiverProjectResolverExtension : AnnotationBasedPluginProjectResolverExtension<SamWithReceiverModel>() {
    companion object {
        val KEY = Key<SamWithReceiverModel>("SamWithReceiverModel")
    }

    override val modelClass get() = SamWithReceiverModel::class.java
    override val userDataKey get() = KEY
}