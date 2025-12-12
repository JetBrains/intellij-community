// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.platform.workspace.jps.entities.SdkEntity

/**
 * Returns the user-visible name of this [SdkEntity]
 *
 * @return name of this [SdkEntity] to be shown to user.
 */
fun SdkEntity.presentableName(): String {
  return "< ${name} >"
}