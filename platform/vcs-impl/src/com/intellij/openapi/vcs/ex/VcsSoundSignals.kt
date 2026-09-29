// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.ide.soundSignals.IdeSoundSignals
import com.intellij.ide.soundSignals.SoundSignal
import com.intellij.ide.soundSignals.SoundSignalProvider
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsSoundSignals {
  private val OWNER = VcsSoundSignals::class.java

  @JvmField
  val GUTTER_INSERTED: SoundSignal = SoundSignal(
    "vcs.gutter.inserted", VcsBundle.messagePointer("sound.signal.vcs.gutter.inserted"),
    "sounds/vcs_gutter_inserted.wav", OWNER, 310,IdeSoundSignals.EDITOR_GUTTER_GROUP,
  )

  @JvmField
  val GUTTER_DELETED: SoundSignal = SoundSignal(
    "vcs.gutter.deleted", VcsBundle.messagePointer("sound.signal.vcs.gutter.deleted"),
    "sounds/vcs_gutter_deleted.wav", OWNER, 320,IdeSoundSignals.EDITOR_GUTTER_GROUP,
  )

  @JvmField
  val GUTTER_MODIFIED: SoundSignal = SoundSignal(
    "vcs.gutter.modified", VcsBundle.messagePointer("sound.signal.vcs.gutter.modified"),
    "sounds/vcs_gutter_modified.wav", OWNER, 330,IdeSoundSignals.EDITOR_GUTTER_GROUP,
  )
}

@ApiStatus.Internal
class VcsSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = with(VcsSoundSignals) {
    listOf(GUTTER_INSERTED, GUTTER_DELETED, GUTTER_MODIFIED)
  }
}
