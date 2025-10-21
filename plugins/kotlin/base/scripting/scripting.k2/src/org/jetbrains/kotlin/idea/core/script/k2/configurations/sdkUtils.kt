// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.workspace.jps.entities.SdkId
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

val ScriptCompilationConfiguration.sdkId: SdkId?
    get() = get(ScriptCompilationConfiguration.jvm.jdkHome)?.absolutePath?.let {
        ExternalSystemJdkUtil.findJdkInSdkTableByPath(it)
    }?.symbolicId


private val Sdk.symbolicId
    get() = SdkId(name, sdkType.name)