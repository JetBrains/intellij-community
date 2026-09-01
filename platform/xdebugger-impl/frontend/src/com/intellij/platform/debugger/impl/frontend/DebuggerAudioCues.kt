// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.audioCues.AudioCue
import com.intellij.ide.audioCues.AudioCueProvider
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DebuggerAudioCues {
  @JvmField
  val BREAKPOINT_LINE: AudioCue = AudioCue(
    "breakpoint.line", XDebuggerBundle.messagePointer("audio.cue.breakpoint.line"),
    "sounds/breakpoint.wav", DebuggerAudioCues::class.java, 10,
  )
}

@ApiStatus.Internal
class DebuggerAudioCueProvider : AudioCueProvider {
  override val audioCues: List<AudioCue> = listOf(DebuggerAudioCues.BREAKPOINT_LINE)
}
