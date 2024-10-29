// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.jps.entities.SdkId

/**
 * Stores additional data associated with the project which currently isn't available in the workspace model.
 */
internal class JpsProjectAdditionalData(
  val projectName: String,
  val projectSdkId: SdkId?,
)