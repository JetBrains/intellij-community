// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.util.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import javax.sound.sampled.LineListener
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the sound read from [streamProvider], which is invoked once, on [Dispatchers.IO], and whose stream is closed here.
 *
 * Suspends until playback stops, or at most a minute; cancellation stops the sound. An unplayable sound or a throwing
 * [streamProvider] propagates, so a caller for which that is not fatal has to catch it.
 *
 * @return `true` if the playback finished, and `false` if the sound did not report the end of the playback in a minute.
 */
suspend fun playSound(streamProvider: () -> InputStream): Boolean {
  return withContext(Dispatchers.IO) {
    AudioSystem.getClip().use { clip ->
      streamProvider().use { raw ->
        val stream = if (raw.markSupported()) raw else BufferedInputStream(raw)
        AudioSystem.getAudioInputStream(stream).use { audioStream ->
          clip.open(audioStream)
        }
      }
      // Bounds a backend that accepts `start()` but never reports the end of playback, which would park the caller forever.
      val finished = withTimeoutOrNull(60.seconds) {
        suspendCancellableCoroutine { continuation ->
          clip.addLineListener(object : LineListener {
            override fun update(event: LineEvent) {
              if (event.type == LineEvent.Type.STOP || event.type == LineEvent.Type.CLOSE) {
                clip.removeLineListener(this)
                continuation.resume(Unit)
              }
            }
          })
          continuation.invokeOnCancellation { clip.stop() }
          clip.start()
        }
      }
      finished != null
    }
  }
}
