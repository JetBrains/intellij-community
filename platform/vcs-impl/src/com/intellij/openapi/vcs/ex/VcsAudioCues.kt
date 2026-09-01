// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.ide.audioCues.AudioCue
import com.intellij.ide.audioCues.AudioCueProvider
import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsAudioCues {
  private val OWNER = VcsAudioCues::class.java

  @JvmField
  val GUTTER_INSERTED: AudioCue =
    AudioCue("vcs.gutter.inserted", VcsBundle.messagePointer("audio.cue.vcs.gutter.inserted"), "sounds/vcs_gutter_inserted.wav", OWNER, 100)

  @JvmField
  val GUTTER_DELETED: AudioCue =
    AudioCue("vcs.gutter.deleted", VcsBundle.messagePointer("audio.cue.vcs.gutter.deleted"), "sounds/vcs_gutter_deleted.wav", OWNER, 110)

  @JvmField
  val GUTTER_MODIFIED: AudioCue =
    AudioCue("vcs.gutter.modified", VcsBundle.messagePointer("audio.cue.vcs.gutter.modified"), "sounds/vcs_gutter_modified.wav", OWNER, 120)
}

@ApiStatus.Internal
class VcsAudioCueProvider : AudioCueProvider {
  override val audioCues: List<AudioCue> = with(VcsAudioCues) {
    listOf(GUTTER_INSERTED, GUTTER_DELETED, GUTTER_MODIFIED)
  }
}
