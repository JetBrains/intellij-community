// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData

@Suppress("UNCHECKED_CAST")
fun DataNode<*>.findChildModuleById(id: String) =
    children.firstOrNull { (it.data as? ModuleData)?.id == id } as? DataNode<out ModuleData>

@Suppress("UNCHECKED_CAST")
fun DataNode<*>.findChildModuleByInternalName(name: String) =
    children.firstOrNull { (it.data as? ModuleData)?.internalName == name } as? DataNode<out ModuleData>