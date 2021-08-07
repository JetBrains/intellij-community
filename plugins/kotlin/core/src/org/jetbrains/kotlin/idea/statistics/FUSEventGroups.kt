// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.statistics

/* Note: along with adding a group to this enum you should also add its GROUP_ID to plugin.xml and get it whitelisted
 * (see https://confluence.jetbrains.com/display/FUS/IntelliJ+Reporting+API).
 *
 * Default value for [events] parameter is intended for collectors which
 *    1. don't have yet a set of allowed values for FUS Whitelist
 *    2. set of possible values is defined by enums in a feature's source code
 */
enum class FUSEventGroups(groupIdSuffix: String, val events: Set<String> = setOf()) {

    Debug("ide.debugger"),
    GradlePerformance("gradle.performance");

    val GROUP_ID: String = "kotlin.$groupIdSuffix"
}

@Suppress("EnumEntryName")
enum class GradleStatisticsEvents {
    Environment,
    Kapt,
    CompilerPlugins,
    MPP,
    JS,
    Libraries,
    GradleConfiguration,
    ComponentVersions,
    KotlinFeatures,
    GradlePerformance,
    UseScenarios
}
