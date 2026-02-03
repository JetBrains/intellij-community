// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.addOptionTag
import com.intellij.util.xmlb.Constants
import org.jdom.Element

@Service(Service.Level.PROJECT)
@State(name = "KotlinGradleFUSSettings", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class KotlinGradleFUSSettings : PersistentStateComponent<Element> {

    var gradleUserDirs: Set<String> = emptySet()

    override fun getState(): Element {
        val element = Element("KotlinGradleFUSSettings")
        gradleUserDirs.forEach {
            element.addOptionTag(KotlinGradleFUSSettings::gradleUserDirs.name, it)
        }
        return element
    }

    override fun loadState(state: Element) {
        val dirs = state.getChildren(Constants.OPTION)
            .filter { it.getAttributeValue(Constants.NAME) == KotlinGradleFUSSettings::gradleUserDirs.name }
            .mapNotNull { it.getAttributeValue(Constants.VALUE) }
            .toSet()
        gradleUserDirs = dirs
    }

    companion object {
        fun getInstance(project: Project): KotlinGradleFUSSettings = project.service()
    }
}