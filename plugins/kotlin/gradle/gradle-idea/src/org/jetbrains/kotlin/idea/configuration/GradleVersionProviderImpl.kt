// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.project.Project
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.extensions.gradle.GradleVersionInfo
import org.jetbrains.kotlin.idea.extensions.gradle.GradleVersionProvider
import org.jetbrains.kotlin.idea.extensions.gradle.MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX_RAW
import org.jetbrains.kotlin.idea.extensions.gradle.scope
import org.jetbrains.plugins.gradle.settings.GradleSettings

internal val MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX = GradleVersion.version(MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX_RAW)

object GradleVersionProviderImpl : GradleVersionProvider {
    fun wrapVersion(version: GradleVersion): GradleVersionInfo {
        return Version(version)
    }

    override fun getVersion(versionString: String): GradleVersionInfo {
        return Version(GradleVersion.version(versionString))
    }

    override fun getCurrentVersionGlobal(): GradleVersionInfo {
        return Version(GradleVersion.current())
    }

    override fun getCurrentVersion(project: Project, path: String): GradleVersionInfo? {
        val settings = GradleSettings.getInstance(project)
        val raw = settings.getLinkedProjectSettings(path)?.resolveGradleVersion() ?: return null
        return Version(raw)
    }

    private class Version(val raw: GradleVersion): GradleVersionInfo {
        override fun compareTo(other: GradleVersionInfo): Int {
            other as? Version ?: error("Can't compare versions from different version providers")
            return raw.compareTo(other.raw)
        }
    }
}

fun GradleVersion.scope(directive: String): String {
    return GradleVersionProviderImpl.wrapVersion(this).scope(directive, GradleVersionProviderImpl)
}