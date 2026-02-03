// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.workspace.jps.entities.SdkId

internal val Sdk.symbolicId
    get() = SdkId(name, sdkType.name)