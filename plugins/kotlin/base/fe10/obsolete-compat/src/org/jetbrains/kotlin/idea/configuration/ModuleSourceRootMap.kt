// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.projectStructure.externalProjectPath' instead",
    ReplaceWith("this.externalProjectPath", imports = ["org.jetbrains.kotlin.idea.base.projectStructure.externalProjectPath"])
)
@Suppress("unused")
val Module.externalProjectPath: String?
    get() = ExternalSystemApiUtil.getExternalProjectPath(this)