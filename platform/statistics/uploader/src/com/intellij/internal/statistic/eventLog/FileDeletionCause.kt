// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import org.jetbrains.annotations.ApiStatus

/**
 * Why a local event-log file was removed by `EventLogFileWriter`.
 *
 * [SEND_REJECTED] means the server rejected the file (HTTP 400) or it failed local validation, so it will never be accepted.
 */
@ApiStatus.Internal
enum class FileDeletionCause { AGE, SIZE_CAP, DISK_FULL, SEND_REJECTED }
