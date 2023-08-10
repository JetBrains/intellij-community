// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ExternalBuildSystemUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module

val Module.externalProjectId: String?
    get() = ExternalSystemApiUtil.getExternalProjectId(this)

val Module.externalProjectPath: String?
    get() = ExternalSystemApiUtil.getExternalProjectPath(this)