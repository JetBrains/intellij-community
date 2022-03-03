// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID

val MAVEN_SYSTEM_ID = ProjectSystemId("Maven")

@Deprecated(
    "Moved to the org.jetbrains.kotlin.idea.configuration package.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID")
)
val GRADLE_SYSTEM_ID: ProjectSystemId
    get() = org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID

@Deprecated(
    "Moved to the org.jetbrains.kotlin.idea.configuration package.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("org.jetbrains.kotlin.idea.configuration.isGradleModule()")
)
fun Module.isGradleModule(): Boolean {
    return ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, this)
}