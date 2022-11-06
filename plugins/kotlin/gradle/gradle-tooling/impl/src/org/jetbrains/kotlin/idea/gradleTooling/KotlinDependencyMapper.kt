// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId

class KotlinDependencyMapper {
    private var currentIndex: KotlinDependencyId = 0
    private val idToDependency = HashMap<KotlinDependencyId, KotlinDependency>()
    private val dependencyToId = HashMap<KotlinDependency, KotlinDependencyId>()

    fun getDependency(id: KotlinDependencyId) = idToDependency[id]

    fun getId(dependency: KotlinDependency): KotlinDependencyId {
        return dependencyToId[dependency] ?: let {
            currentIndex++
            dependencyToId[dependency] = currentIndex
            idToDependency[currentIndex] = dependency
            return currentIndex
        }
    }

    fun toDependencyMap(): Map<KotlinDependencyId, KotlinDependency> = idToDependency
}