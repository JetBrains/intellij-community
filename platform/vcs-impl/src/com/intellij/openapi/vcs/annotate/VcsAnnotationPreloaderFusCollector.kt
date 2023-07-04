// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate

import com.intellij.codeInsight.daemon.impl.DaemonFusCollector
import com.intellij.internal.statistic.eventLog.events.EventFields

internal object VcsAnnotationPreloaderFusCollector {
  val ANNOTATION_LOADED = DaemonFusCollector.GROUP.registerEvent("vcs.annotation.loaded", EventFields.DurationMs)
}