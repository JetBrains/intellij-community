// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkEntities")
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.SdkEntity
import org.jetbrains.annotations.ApiStatus

val SdkEntity.presentableName: String
  @ApiStatus.Experimental
  get() ="< $name >"
