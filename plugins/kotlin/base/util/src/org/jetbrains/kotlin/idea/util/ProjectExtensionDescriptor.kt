// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

abstract class ProjectExtensionDescriptor<T : Any>(val name: String) {
    val extensionPointName: ExtensionPointName<T> = ExtensionPointName.create(name)

    fun getInstances(project: Project): List<T> {
        val projectArea = project.extensionArea
        if (!projectArea.hasExtensionPoint(extensionPointName.name)) return listOf()

        return projectArea.getExtensionPoint(extensionPointName).extensions.toList()
    }
}
