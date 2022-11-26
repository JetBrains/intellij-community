/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.gradleJava

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AnnotationBasedPluginProjectResolverExtension
import org.jetbrains.kotlin.idea.gradleTooling.model.assignment.AssignmentModel

class AssignmentProjectResolverExtension : AnnotationBasedPluginProjectResolverExtension<AssignmentModel>() {
    companion object {
        val KEY = Key<AssignmentModel>("AssignmentModel")
    }

    override val modelClass get() = AssignmentModel::class.java
    override val userDataKey get() = KEY
}