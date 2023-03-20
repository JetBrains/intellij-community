// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.plugins.gradle.settings.GradleSettings

interface GradleVersionInfo : Comparable<GradleVersionInfo>

object GradleVersionProvider {
    fun wrapVersion(version: GradleVersion): GradleVersionInfo {
        return OpaqueGradleVersion(version)
    }

    fun getVersion(versionString: String): GradleVersionInfo {
        return OpaqueGradleVersion(GradleVersion.version(versionString))
    }

    fun getCurrentVersionGlobal(): GradleVersionInfo {
        return OpaqueGradleVersion(GradleVersion.current())
    }

    fun getCurrentVersion(project: Project, path: String): GradleVersionInfo? {
        val settings = GradleSettings.getInstance(project)
        val raw = settings.getLinkedProjectSettings(path)?.resolveGradleVersion() ?: return null
        return OpaqueGradleVersion(raw)
    }

    private class OpaqueGradleVersion(val raw: GradleVersion): GradleVersionInfo {
        override fun compareTo(other: GradleVersionInfo): Int {
            other as? OpaqueGradleVersion ?: error("Can't compare versions from different version providers")
            return raw.compareTo(other.raw)
        }
    }
}

fun GradleVersion.scope(directive: String): String {
    return GradleVersionProvider.wrapVersion(this).scope(directive)
}

private const val MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION_RAW: String = "3.4"

fun GradleVersionInfo.scope(directive: String): String {
    if (this < GradleVersionProvider.getVersion(MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION_RAW)) {
        return when (directive) {
            "implementation" -> "compile"
            "testImplementation" -> "testCompile"
            else -> throw IllegalArgumentException("Unknown directive `$directive`")
        }
    }

    return directive
}

fun GradleVersionProvider.fetchGradleVersion(file: PsiFile): GradleVersionInfo {
    return gradleVersionFromFile(file) ?: getCurrentVersionGlobal()
}

private fun GradleVersionProvider.gradleVersionFromFile(psiFile: PsiFile): GradleVersionInfo? {
    val module = psiFile.module ?: return null
    val path = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return getCurrentVersion(module.project, path)
}