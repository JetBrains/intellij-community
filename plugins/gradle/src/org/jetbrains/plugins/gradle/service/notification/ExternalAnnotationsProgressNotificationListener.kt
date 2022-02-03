// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import java.util.*

/**
 * Defines contract for callback to listen external annotations resolving notifications.
 *
 * @author Viktor Noskin
 */
interface ExternalAnnotationsProgressNotificationListener : EventListener {
  fun onStartResolve(id: ExternalAnnotationsTaskId)

  fun onFinishResolve(id: ExternalAnnotationsTaskId)
}