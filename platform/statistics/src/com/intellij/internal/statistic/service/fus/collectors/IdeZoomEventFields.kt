// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.eventLog.events.EventFields

object IdeZoomEventFields {
  enum class ZoomMode { ZOOM_IN, ZOOM_OUT }
  enum class Place { SETTINGS, SWITCHER }

  val zoomMode = EventFields.Enum("zoom_mode", ZoomMode::class.java)
  val place = EventFields.Enum("zoom_place", Place::class.java)
  val zoomScalePercent = EventFields.Int("zoom_scale_percent")
  val presentationMode = EventFields.Boolean("presentation_mode")

  // Switcher fields
  val finalZoomScalePercent = EventFields.Int("final_zoom_scale_percent")
  val applied = EventFields.Boolean("applied")
}