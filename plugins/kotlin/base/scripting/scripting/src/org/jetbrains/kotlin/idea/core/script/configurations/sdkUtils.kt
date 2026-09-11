// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.configurations

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.util.SystemProperties
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

val ScriptCompilationConfiguration.sdkId: SdkId?
    get() = get(ScriptCompilationConfiguration.jvm.jdkHome)?.absolutePath?.let {
        ExternalSystemJdkUtil.findJdkInSdkTableByPath(it)
    }?.symbolicId

/** The Java home to use when the SDK table holds no JDK. Takes `JAVA_HOME` first, then the IDE runtime. */
val defaultJavaHome: String?
    get() = sequenceOf(System.getenv("JAVA_HOME"), SystemProperties.getJavaHome())
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .firstOrNull { JavaSdk.getInstance().isValidSdkHome(it) }

private val Sdk.symbolicId
    get() = SdkId(name, sdkType.name)
