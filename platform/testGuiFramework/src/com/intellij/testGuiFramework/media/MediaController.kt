/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.media

import com.sun.javafx.application.PlatformImpl
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.stage.Stage
import javafx.util.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


object MediaController {

  val runnableQueue = LinkedBlockingQueue<Runnable>()

  private val clickSound by lazy { getMediaByName("/click.mp3") }
  private var clicker: MediaPlayer? = null
  private var clicker1: MediaPlayer? = null

  private val typeSound by lazy { getMediaByName("/type.mp3") }
  private var typer: MediaPlayer? = null
  private var typer1: MediaPlayer? = null


  private val loungeMusic by lazy { getMediaByName("/click.mp3") }
  val initCdl = CountDownLatch(1)
  var wasStarted = false

  fun start() {
    if (wasStarted) {
      if (clicker != null) clicker!!.dispose()
      if (clicker1 != null) clicker1!!.dispose()
      if (typer != null) typer!!.dispose()
      if (typer1 != null) typer1!!.dispose()
      initClickerAndTyper()
      return
    }
    thread(name = "JavaFX starter") { Application.launch(FxApp::class.java) }
    PlatformImpl.startup({ initCdl.countDown() })
    initCdl.await()
    initClickerAndTyper()
    wasStarted = true
  }

  fun initClickerAndTyper() {
    clicker = MediaPlayer(clickSound)
    clicker!!.setVolume(0.5)
    clicker1 = MediaPlayer(clickSound)
    clicker1!!.setVolume(0.5)
    typer = MediaPlayer(typeSound)
    typer!!.setVolume(0.5)
    typer1 = MediaPlayer(typeSound)
    typer1!!.setVolume(0.5)
  }

  fun click() {
    runnableQueue.put(Runnable {
      if (clicker!!.status == MediaPlayer.Status.READY) {
        clicker!!.seek(clicker!!.startTime)
        clicker!!.play()
      }
      else {
        clicker1!!.seek(clicker!!.startTime)
        clicker1!!.play()
      }
    })
  }

  fun type() {
    runnableQueue.put(Runnable {
      if (typer!!.status == MediaPlayer.Status.READY) {
        typer!!.seek(clicker!!.startTime)
        typer!!.play()
      }
      else {
        typer1!!.seek(clicker!!.startTime)
        typer1!!.play()
      }
    })
  }

  fun playLounge() {
    runnableQueue.put(Runnable {
      val player = MediaPlayer(loungeMusic)
      player.setOnEndOfMedia { player.dispose() }
      player.play()
    })
  }

  private fun getMediaByName(name: String): Media
    = Media(javaClass.getResource(name).toURI().toString())

  fun withMedia(mediaName: String, runnable: Playback.() -> Unit) {
    val player = MediaPlayer(getMediaByName(mediaName))
    runnable(Playback(player, runnableQueue))
  }

  fun inSeconds(sec: Int) = 1.0 * sec * 1000
  fun inMinutes(min: Int) = inSeconds(min * 60)

  //23:59:47.235 "HH:mm:ss.SSS"
  fun inTime(timeString: String): Double {
    var hours: Int = 0
    var minutes: Int = 0
    var seconds: Int = 0
    var millis: Int = 0

    if (Regex("\\d\\d:\\d\\d:\\d\\d\\.\\d+").matches(timeString)) {
      val splitted = timeString.split(":")
      hours = splitted[0].toInt()
      minutes = splitted[1].toInt()
      val (secondsStr, millisStr) = splitted[2].split(".")
      seconds = secondsStr.toInt()
      millis = millisStr.toInt()
    }
    else if (Regex("\\d\\d:\\d\\d\\.\\d+").matches(timeString)) {
      val splitted = timeString.split(":")
      minutes = splitted[0].toInt()
      val (secondsStr, millisStr) = splitted[1].split(".")
      seconds = secondsStr.toInt()
      millis = millisStr.toInt()
    }
    else if (Regex("\\d\\d:\\d\\d").matches(timeString)) {
      val splitted = timeString.split(":")
      minutes = splitted[0].toInt()
      seconds = splitted[1].toInt()
    }
    return 1.0 * millis.toInt() + seconds.toInt() * 1000 + minutes.toInt() * 60000 + hours.toInt() * 3600000
  }

  class FxApp : Application() {

    override fun start(primaryStage: Stage) {
      val root = Group()
      val scene = Scene(root, 500.0, 200.0)

      val player = MediaPlayer(loungeMusic)
      val mediaView = MediaView(player)
      (scene.root as Group).children.add(mediaView)

      thread(name = "Media Task Queue") {
        while (true) {
          val runnable = runnableQueue.take()
          Platform.runLater(runnable)
          Thread.sleep(80)
        }
      }
    }
  }

  class Playback(val player: MediaPlayer, runnableQueue: LinkedBlockingQueue<Runnable>) {

    fun playByText(transcriptionText: String, runnable: () -> Unit) {
      val (start, end) = TranscriptReader.getTimeInterval(transcriptionText)
      play(start, end, runnable)
    }

    fun play(from: Double? = null, to: Double, runnable: () -> Unit) {
      val cdl = CountDownLatch(1)
      val key = "offset marker $to"
      runnableQueue.put(Runnable {
        if (from != null) player.seek(Duration(from))
        player.media.markers.put(key, Duration.millis(to))
        player.setOnMarker { mediaMarkerEvent -> if (mediaMarkerEvent.marker.key == key) player.pause(); player.media.markers.remove(key); cdl.countDown() }
        player.play()
      })
      runnable()
      cdl.await()
    }


  }

}

fun main(args: Array<String>) {

  MediaController.start()

  MediaController.withMedia("/param_hints.mp3") {
    playByText("Alt + Enter") {

    }
    playByText("turn hints back") {

    }
  }

}