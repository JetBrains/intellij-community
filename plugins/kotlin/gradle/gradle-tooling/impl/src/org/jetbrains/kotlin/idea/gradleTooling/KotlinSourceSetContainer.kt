// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.closure

interface KotlinSourceSetContainer {
    val sourceSetsByName: Map<String, KotlinSourceSet>
}

val KotlinSourceSetContainer.sourceSets: List<KotlinSourceSet> get() = sourceSetsByName.values.toList()

fun KotlinSourceSetContainer.resolveDeclaredDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return sourceSet.declaredDependsOnSourceSets.mapNotNull { name -> sourceSetsByName[name] }.toSet() - sourceSet
}

fun KotlinSourceSetContainer.resolveAllDependsOnSourceSets(sourceSet: KotlinSourceSet): Set<KotlinSourceSet> {
    return sourceSet.closure { currentSourceSet ->
        currentSourceSet.declaredDependsOnSourceSets.mapNotNull { dependsOnSourceSet ->
            sourceSetsByName[dependsOnSourceSet]
        }
    }
}

fun KotlinSourceSetContainer.resolveAllDependsOnSourceSets(sourceSets: Iterable<KotlinSourceSet>): Set<KotlinSourceSet> {
    return sourceSets.closure<KotlinSourceSet> { sourceSet ->
        sourceSet.declaredDependsOnSourceSets.mapNotNull { dependsOnSourceSet -> sourceSetsByName[dependsOnSourceSet] }
    }
}

fun KotlinSourceSetContainer.isDependsOn(from: KotlinSourceSet, to: KotlinSourceSet): Boolean {
    return to in resolveAllDependsOnSourceSets(from)
}

fun KotlinSourceSet.isDependsOn(model: KotlinSourceSetContainer, sourceSet: KotlinSourceSet): Boolean {
    return model.isDependsOn(from = this, to = sourceSet)
}
