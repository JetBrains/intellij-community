// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.annotations.ApiStatus

val MAVEN_SYSTEM_ID = ProjectSystemId("Maven")

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("Moved to the org.jetbrains.kotlin.idea.configuration package.")
@Deprecated(
    "Moved to the org.jetbrains.kotlin.idea.configuration package.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID")
)
val GRADLE_SYSTEM_ID: ProjectSystemId
    get() = org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID
