// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2NativeCompilerArguments
import org.jetbrains.kotlin.idea.util.FactoryCopyableDataNodeUserDataProperty
import org.jetbrains.kotlin.tooling.core.Interner

internal val DataNode<out ProjectData>.interner: Interner by FactoryCopyableDataNodeUserDataProperty(
    key = Key.create("org.jetbrains.kotlin.idea.gradleJava.interner"),
    factory = ::Interner
)

@get:JvmName("getInternerFromModuleDataNode")
internal val DataNode<out ModuleData>.interner: Interner
    get() {
        val projectNode = ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT) ?: error("Missing project node for $this")
        return projectNode.interner
    }

internal fun CommonCompilerArguments.internLargeArguments(interner: Interner) {
    fun Array<String>.internElements() = forEachIndexed { index, s -> this[index] = interner.getOrPut(s) }

    if (this is K2JVMCompilerArguments) {
        classpath?.intern()
        javaModulePath?.intern()
        friendPaths?.internElements()
        additionalJavaModules?.internElements()
    }

    if (this is K2NativeCompilerArguments) {
        libraries?.internElements()
        linkerArguments?.internElements()
        singleLinkerArguments?.internElements()
    }

    optIn?.internElements()
    pluginOptions?.internElements()
    pluginClasspaths?.internElements()
    pluginConfigurations?.internElements()
    freeArgs = freeArgs.map { interner.getOrPut(it) }
}
