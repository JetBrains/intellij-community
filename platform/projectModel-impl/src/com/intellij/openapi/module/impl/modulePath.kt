// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.platform.workspace.jps.serialization.impl.ModulePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use ModulePath.getModuleNameByFilePath instead", ReplaceWith("ModulePath.getModuleNameByFilePath(path)"))
fun getModuleNameByFilePath(path: String): String {
  return ModulePath.getModuleNameByFilePath(path)
}