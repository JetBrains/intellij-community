// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.workspace.jps.JpsMetrics
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val jpsMetrics: JpsMetrics by lazy { JpsMetrics.getInstance() }
