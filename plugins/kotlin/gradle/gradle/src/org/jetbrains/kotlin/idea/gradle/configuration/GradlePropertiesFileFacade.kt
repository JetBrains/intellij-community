// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.model.ExternalProject
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@IntellijInternalApi
class GradlePropertiesFileFacade(private val baseDir: String) {

    fun readProperty(propertyName: String): String? {

        val baseVirtualDir = LocalFileSystem.getInstance().findFileByPath(baseDir) ?: return null

        for (propertyFileName in GRADLE_PROPERTY_FILES) {
            val propertyFile = baseVirtualDir.findChild(propertyFileName) ?: continue
            Properties().also { it.load(propertyFile.inputStream) }.getProperty(propertyName)?.let {
                return it
            }
        }

        return null
    }

    fun addCodeStyleProperty(value: String) {
        addProperty(KOTLIN_CODE_STYLE_GRADLE_SETTING, value)
    }

    private fun addProperty(key: String, value: String) {
        val projectPropertiesPath = Path(baseDir, GRADLE_PROPERTIES_FILE_NAME)

        val keyValue = "$key=$value"

        val updatedText = if (projectPropertiesPath.exists()) {
            projectPropertiesPath.readText() + System.lineSeparator() + keyValue
        } else {
            keyValue
        }

        projectPropertiesPath.writeText(updatedText)
    }

    companion object {

        fun forProject(project: Project) = GradlePropertiesFileFacade(project.basePath!!)

        fun forExternalProject(externalProject: ExternalProject) = GradlePropertiesFileFacade(externalProject.projectDir.path)

        const val KOTLIN_CODE_STYLE_GRADLE_SETTING = "kotlin.code.style"

        private const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
        private const val GRADLE_PROPERTIES_LOCAL_FILE_NAME = "local.properties"

        private val GRADLE_PROPERTY_FILES = listOf(GRADLE_PROPERTIES_LOCAL_FILE_NAME, GRADLE_PROPERTIES_FILE_NAME)
    }
}

fun readGradleProperty(project: Project, key: String): String? {
    return GradlePropertiesFileFacade.forProject(project).readProperty(key)
}