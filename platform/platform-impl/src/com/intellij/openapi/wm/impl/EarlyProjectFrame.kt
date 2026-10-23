// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.impl.createIdeFrame
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.ui.ColorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.JBR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.awt.Frame
import java.awt.Rectangle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val LOG = logger<EarlyProjectFrame>()

/**
 * The project frame window, shown as soon as the base look and feel and the scale exist, before the application finishes its
 * initialization. The first project adopts it through the frame allocator; a start that opens no project disposes it.
 * The frame opens at the bounds the window manager saved last, read from a small file in the config directory.
 * Without that file, or when the start is not going to open a project, no early frame appears.
 */
@Internal
object EarlyProjectFrame {
  val isEnabled: Boolean
    get() = EarlyAccessRegistryManager.getBoolean("ide.startup.early.frame")

  /** `true` after [isExpected] decided that this start opens a project into the early frame. */
  @Volatile
  var isExpected: Boolean = false
    private set

  private val savedState: EarlyFrameState? by lazy { readState() }

  private val pending = AtomicReference<IdeFrameImpl?>()

  @Volatile
  private var shownFrame: IdeFrameImpl? = null

  @Volatile
  private var shownAt = 0L

  private var created: IdeFrameImpl? = null

  /**
   * Decides whether this start opens a project: a start without arguments always does, with the reopened project or the
   * welcome screen project; with arguments the state saved by the last session decides.
   */
  fun isExpected(args: List<String>): Boolean {
    val state = savedState ?: return false
    isExpected = args.isEmpty() || state.projectOpen
    return isExpected
  }

  /** Creates the frame without showing it; the creation reads no settings, so it can run before the scale is known. */
  @RequiresEdt
  fun create() {
    if (created != null) {
      return
    }
    val state = savedState
    created = runActivity("early project frame creation") {
      val frame = createIdeFrame(FrameInfo().also {
        if (state != null) {
          it.bounds = state.bounds
          it.extendedState = state.extendedState
        }
      })
      // a solid window in the editor background of the theme; the components arrive with the project
      state?.background?.let {
        @Suppress("UseJBColor")
        val background = Color(it)
        frame.background = background
        frame.contentPane.background = background
      }
      // the merged title bar of the project frame, so the title bar melds with the window and the controls stay in place
      JBR.getWindowDecorations()?.let { decorations ->
        val titleBar = decorations.createCustomTitleBar()
        titleBar.height = CustomWindowHeaderUtil.getPreferredWindowHeaderHeight(isCompactHeader = false).toFloat()
        titleBar.putProperty("controls.dark", ColorUtil.isDark(frame.contentPane.background))
        decorations.setCustomTitleBar(frame, titleBar)
      }
      frame
    }
  }

  @RequiresEdt
  fun show() {
    create()
    val frame = created!!
    savedState?.let {
      frame.isUndecorated = it.undecorated
      frame.nativeTitleDecided = true
    }
    runActivity("early project frame showing") { frame.isVisible = true }
    shownAt = System.nanoTime()
    shownFrame = frame
    pending.set(frame)
    StartUpMeasurer.addInstantEvent("early project frame shown")
  }

  fun take(): IdeFrameImpl? {
    val frame = pending.get() ?: return null
    // a displayable frame cannot change its decoration; a frame with a stale one stays pending and is disposed after the project opens
    if (frame.isUndecorated != hideNativeLinuxTitle(UISettings.shadowInstance)) {
      LOG.info("The early project frame has a stale title decoration, a new frame is created")
      return null
    }
    return pending.getAndSet(null)
  }

  /** The `System.nanoTime` at which [frame] became visible, or 0 when it is not the early frame. */
  fun shownAtOf(frame: IdeFrameImpl?): Long = if (frame != null && frame === shownFrame) shownAt else 0L

  suspend fun disposeIfUnused() {
    val frame = pending.getAndSet(null) ?: return
    withContext(Dispatchers.EDT) {
      frame.doDispose()
    }
  }

  /** Called when a project frame is released, so the next start opens the frame where this one left it. */
  fun saveState(info: FrameInfo, undecorated: Boolean, projectOpen: Boolean, background: Color) {
    val bounds = info.bounds ?: return
    try {
      val state = EarlyFrameState(bounds, info.extendedState, undecorated, projectOpen, background.rgb and 0xffffff)
      stateFile().writeText(formatEarlyFrameState(state))
    }
    catch (e: IOException) {
      LOG.warn("Cannot save the early frame state", e)
    }
  }

  private fun readState(): EarlyFrameState? {
    val file = stateFile()
    try {
      val state = if (Files.isRegularFile(file)) parseEarlyFrameState(file.readText()) else null
      // the saved color is exact; before the first save the theme state decides
      return if (state == null || state.background != null) state else state.copy(background = themeBackground(readThemeId()))
    }
    catch (e: IOException) {
      LOG.warn("Cannot read the early frame state", e)
      return null
    }
  }

  // the theme state names the theme only when it is not the default one
  private fun readThemeId(): String? {
    val file = Path.of(PathManager.getOptionsPath(), "laf.xml")
    try {
      if (!Files.isRegularFile(file)) {
        return null
      }
      return Regex("""<laf\s+themeId="([^"]*)"""").find(file.readText())?.groupValues?.get(1)
    }
    catch (e: IOException) {
      LOG.warn("Cannot read the theme state", e)
      return null
    }
  }

  private fun stateFile(): Path = PathManager.getConfigDir().resolve("early-frame.txt")
}

/**
 * [bounds] are in the device space, as `FrameInfo.bounds`. [projectOpen] tells whether the next start opens a project.
 * [background] is the RGB of the editor background of the theme, which the project frame paints first.
 */
@VisibleForTesting
internal data class EarlyFrameState(
  val bounds: Rectangle,
  val extendedState: Int,
  val undecorated: Boolean,
  val projectOpen: Boolean,
  val background: Int? = null,
)

/** The editor background of the bundled themes; the platform default theme is Islands Dark. */
@VisibleForTesting
internal fun themeBackground(themeId: String?): Int = when {
  themeId == null || themeId == "Islands Dark" -> 0x191a1c
  themeId == "ExperimentalDark" -> 0x1e1f22
  themeId == "Darcula" || themeId == "Islands Darcula" -> 0x2b2b2b
  themeId.contains("HighContrast") -> 0x000000
  themeId.contains("Light", ignoreCase = true) -> 0xffffff
  else -> 0x1e1f22
}

@VisibleForTesting
internal fun formatEarlyFrameState(state: EarlyFrameState): String {
  val b = state.bounds
  return "bounds=${b.x},${b.y},${b.width},${b.height}\n" +
         "extendedState=${state.extendedState}\n" +
         "undecorated=${state.undecorated}\n" +
         "projectOpen=${state.projectOpen}\n" +
         (state.background?.let { "background=#%06x\n".format(it) } ?: "")
}

@VisibleForTesting
internal fun parseEarlyFrameState(text: String): EarlyFrameState? {
  val values = HashMap<String, String>()
  for (line in text.lineSequence()) {
    val index = line.indexOf('=')
    if (index > 0) {
      values[line.substring(0, index).trim()] = line.substring(index + 1).trim()
    }
  }
  val bounds = values["bounds"]?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
  if (bounds.size != 4 || bounds[2] <= 0 || bounds[3] <= 0) {
    return null
  }
  return EarlyFrameState(
    bounds = Rectangle(bounds[0], bounds[1], bounds[2], bounds[3]),
    extendedState = values["extendedState"]?.toIntOrNull() ?: Frame.NORMAL,
    undecorated = values["undecorated"].toBoolean(),
    projectOpen = values["projectOpen"].toBoolean(),
    background = values["background"]?.removePrefix("#")?.toIntOrNull(16),
  )
}
