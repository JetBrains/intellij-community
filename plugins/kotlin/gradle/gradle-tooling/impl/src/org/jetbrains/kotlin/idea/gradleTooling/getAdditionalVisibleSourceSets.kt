// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import java.lang.reflect.Method

internal fun getAdditionalVisibleSourceSets(project: Project, sourceSetName: String): Set<String> {
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return emptySet()
    val kotlinExtensionClass = kotlinExtension.javaClass
    val getSourceSets = kotlinExtensionClass.getMethodOrNull("getSourceSets") ?: return emptySet()
    val sourceSets = getSourceSets.invoke(kotlinExtension) as NamedDomainObjectCollection<*>
    val sourceSet = sourceSets.findByName(sourceSetName) as? Named ?: return emptySet()
    return getAdditionalVisibleSourceSets(project, sourceSet)
}

internal fun getAdditionalVisibleSourceSets(project: Project, sourceSet: Named): Set<String> {
    val sourceSetClass = sourceSet.javaClass
    val getAdditionalVisibleSourceSets = sourceSetClass.getMethodOrNull("getAdditionalVisibleSourceSets") ?: return emptySet()

    /*
    Invoke 'getAdditionalVisibleSourceSets' catching, since this method threw exceptions in older versions
    of the Gradle plugin. In particular: Some tests experienced 'NoSuchElementException' for certain source sets, that
    should be available.
     */
    val additionalVisibleSourceSets = getAdditionalVisibleSourceSets.invokeCatching(project, sourceSet)?.let { it as List<*> }
    return additionalVisibleSourceSets.orEmpty().map { it as Named }.map { it.name }.toSet()
}

private fun Method.invokeCatching(project: Project, obj: Any, vararg args: Any?): Any? {
    return try {
        invoke(obj, *args)
    } catch (t: Throwable) {
        project.logger.warn("Method invocation $name failed", t)
        null
    }
}