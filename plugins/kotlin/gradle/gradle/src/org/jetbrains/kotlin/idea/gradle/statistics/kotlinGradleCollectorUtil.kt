// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.text.trimMiddle
import java.util.Locale

/**
 * Anonymizes a Gradle project path for FUS reporting.
 *
 * This duplicates the logic of `ProjectUtil.getProjectCacheFileName`, which cannot be reused directly:
 * 1. the path of a Gradle project may not have a corresponding [com.intellij.openapi.project.Project];
 * 2. the projectId should be stable and independent of the IDE version.
 *
 */
internal fun anonymizeProjectPathForFus(path: String): String {
    val presentableUrl = FileUtil.toSystemIndependentName(path)
    val name = PathUtilRt.getFileName(presentableUrl).lowercase(Locale.US).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
    val locationHash = Integer.toHexString(presentableUrl.hashCode())
    val projectHash =
        "${name.trimMiddle(name.length.coerceAtMost(254 - locationHash.length), useEllipsisSymbol = false)}.$locationHash"
    @Suppress("DEPRECATION")
    return EventLogConfiguration.getInstance().anonymize(projectHash)
}

internal val anonymizedProjectRegexp = "([0-9A-Fa-f]{40,64})|undefined"