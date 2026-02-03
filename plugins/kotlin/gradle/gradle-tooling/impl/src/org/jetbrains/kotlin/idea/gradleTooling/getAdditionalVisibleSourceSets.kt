// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinSourceSetReflection

internal fun getAdditionalVisibleSourceSets(project: Project, sourceSetName: String): Set<String> {
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return emptySet()
    val kotlinExtensionClass = kotlinExtension.javaClass
    val getSourceSets = kotlinExtensionClass.getMethodOrNull("getSourceSets") ?: return emptySet()
    val sourceSets = getSourceSets.invoke(kotlinExtension) as NamedDomainObjectCollection<*>
    val sourceSet = sourceSets.findByName(sourceSetName) as? Named ?: return emptySet()
    return getAdditionalVisibleSourceSets(sourceSet)
}

internal fun getAdditionalVisibleSourceSets(sourceSet: Named): Set<String> {
    return KotlinSourceSetReflection(sourceSet).additionalVisibleSourceSets.map { it.name }.toSet()
}
