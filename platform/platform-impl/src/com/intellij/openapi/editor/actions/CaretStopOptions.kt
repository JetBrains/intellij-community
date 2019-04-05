// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag


data class CaretStop
@JvmOverloads constructor(@Attribute("start") val atStart: Boolean = false,
                          @Attribute("end") val atEnd: Boolean = false) { // @formatter:off
  companion object {
    @JvmField val NONE  = CaretStop(atStart = false, atEnd = false)
    @JvmField val START = CaretStop(atStart = true,  atEnd = false)
    @JvmField val END   = CaretStop(atStart = false, atEnd = true)
    @JvmField val BOTH  = CaretStop(atStart = true,  atEnd = true)
  }
} // @formatter:on

data class CaretStopPolicy
@JvmOverloads constructor(@OptionTag("BACKWARD") val backward: CaretStop = CaretStop.NONE,
                          @OptionTag("FORWARD") val forward: CaretStop = CaretStop.NONE) { // @formatter:off
  companion object {
    @JvmField val NONE      = CaretStopPolicy(backward = CaretStop.NONE,  forward = CaretStop.NONE)
    @JvmField val CURRENT   = CaretStopPolicy(backward = CaretStop.START, forward = CaretStop.END)
    @JvmField val NEIGHBOR  = CaretStopPolicy(backward = CaretStop.END,   forward = CaretStop.START)
    @JvmField val START     = CaretStopPolicy(backward = CaretStop.START, forward = CaretStop.START)
    @JvmField val END       = CaretStopPolicy(backward = CaretStop.END,   forward = CaretStop.END)
    @JvmField val BOTH      = CaretStopPolicy(backward = CaretStop.BOTH,  forward = CaretStop.BOTH)
  }
} // @formatter:on

data class CaretStopOptions(@OptionTag("WORD_BOUNDARY") val wordBoundaryPolicy: CaretStopPolicy = CaretStopPolicy.NONE,
                            @OptionTag("LINE_BOUNDARY") val lineBoundaryPolicy: CaretStopPolicy = CaretStopPolicy.NONE) {
  constructor() : this(if (SystemInfo.isWindows) CaretStopPolicy.START else CaretStopPolicy.CURRENT,
                       if (SystemInfo.isWindows) CaretStopPolicy.BOTH else CaretStopPolicy.NONE)
}
