// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.soundSignals.IdeSoundSignals
import com.intellij.ide.soundSignals.SoundSignal
import com.intellij.ide.soundSignals.SoundSignalProvider
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DebuggerSoundSignals {
  @JvmField
  val BREAKPOINT_HIT: SoundSignal = SoundSignal(
    "breakpoint.hit", XDebuggerBundle.messagePointer("sound.signal.breakpoint.hit"),
    "sounds/breakpoint.wav", DebuggerSoundSignals::class.java, 20, IdeSoundSignals.EVENTS_GROUP,
  )

  @JvmField
  val BREAKPOINT_LINE: SoundSignal = SoundSignal(
    "breakpoint.line", XDebuggerBundle.messagePointer("sound.signal.breakpoint.line"),
    "sounds/breakpoint.wav", DebuggerSoundSignals::class.java, 300, IdeSoundSignals.EDITOR_GUTTER_GROUP,
  )
}

@ApiStatus.Internal
class DebuggerSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = listOf(DebuggerSoundSignals.BREAKPOINT_HIT, DebuggerSoundSignals.BREAKPOINT_LINE)
}
