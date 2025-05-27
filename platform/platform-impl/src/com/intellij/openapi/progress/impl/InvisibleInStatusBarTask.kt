// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.Progressive
import com.intellij.openapi.progress.TaskInfo
import org.jetbrains.annotations.ApiStatus

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * See {@link ProgressIndicator} notice.
 * </p>
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
interface InvisibleInStatusBarTask: TaskInfo, Progressive, PerformInBackgroundOption