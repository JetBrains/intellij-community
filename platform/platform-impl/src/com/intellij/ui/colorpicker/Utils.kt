/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:ApiStatus.Internal

package com.intellij.ui.colorpicker

import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * Convert (Alpha + HSB) color format to ARGB format.
 */
fun ahsbToArgb(alpha: Int, hue: Float, saturation: Float, brightness: Float): Int =
  (alpha shl 24) or (Color.HSBtoRGB(hue, saturation, brightness) and 0x00FFFFFF)
