// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.application.options.editor.EditorOptionsListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
interface TerminalUiSettingsListener {
  fun cursorChanged() {}
  fun fontChanged() {}
}

@State(name = "TerminalUiSettingsManager", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
@Service(Service.Level.APP)
class TerminalUiSettingsManager internal constructor() : PersistentStateComponent<TerminalUiSettingsManager.State>, Disposable {
  var editorColorsScheme: EditorColorsScheme
    private set
  private var cachedColorPalette: JBTerminalSchemeColorPalette? = null
  private var fontSize = -1f
  private val listeners: MutableList<TerminalUiSettingsListener> = CopyOnWriteArrayList()
  private var state = State()

  init {
    editorColorsScheme = EditorColorsManager.getInstance().globalScheme
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      resetFontSize() // Enter/Exit Presentation Mode
    })
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { _ ->
      // ANSI colors changed, Console Font changed
      editorColorsScheme = EditorColorsManager.getInstance().globalScheme
      cachedColorPalette = null
      resetFontSize()
    })
    connection.subscribe(EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC, EditorOptionsListener {
      fireCursorUpdate() // update on "Caret blinking" changes in "Editor | General | Appearance"
    })
  }

  fun getTerminalColorPalette(): TerminalColorPalette {
    var palette = cachedColorPalette
    if (palette == null) {
      palette = JBTerminalSchemeColorPalette(editorColorsScheme)
      cachedColorPalette = palette
    }
    return palette
  }

  @JvmName("addListener")
  internal fun addListener(parentDisposable: Disposable, listener: TerminalUiSettingsListener) {
    listeners.add(listener)
    Disposer.register(parentDisposable) { listeners.remove(listener) }
  }

  var cursorShape: CursorShape
    get() = state.cursorShape
    set(value) {
      state.cursorShape = value
      fireCursorUpdate()
    }

  private fun fireCursorUpdate() {
    for (listener in listeners) {
      listener.cursorChanged()
    }
  }

  var maxVisibleCompletionItemsCount: Int
    get() = state.maxVisibleCompletionItemsCount
    set(value) {
      state.maxVisibleCompletionItemsCount = value
    }

  var autoShowDocumentationPopup: Boolean
    get() = state.autoShowDocumentationPopup
    set(value) {
      state.autoShowDocumentationPopup = value
    }

  private fun fireFontChanged() {
    for (listener in listeners) {
      listener.fontChanged()
    }
  }

  fun getFontSize(): Float {
    if (fontSize <= 0) {
      fontSize = detectFontSize()
    }
    return fontSize
  }

  fun setFontSize(newFontSize: Int) {
    setFontSize(newFontSize.toFloat())
  }

  fun setFontSize(newFontSize: Float) {
    val prevFontSize = fontSize
    fontSize = newFontSize
    if (prevFontSize != fontSize) {
      fireFontChanged()
    }
  }

  private fun detectFontSize(): Float {
    return if (UISettings.getInstance().presentationMode) {
      UISettingsUtils.getInstance().presentationModeFontSize
    }
    else UISettingsUtils.getInstance().scaledConsoleFontSize
  }

  fun resetFontSize() {
    setFontSize(detectFontSize())
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): TerminalUiSettingsManager = service()
  }

  /** [org.jetbrains.plugins.terminal.fus.TerminalSettingsStateCollector.GROUP] must be updated if any new value added or renamed. */
  enum class CursorShape(val text: @Nls String) {
    BLOCK(IdeBundle.message("terminal.cursor.shape.block.name")),
    UNDERLINE(IdeBundle.message("terminal.cursor.shape.underline.name")),
    VERTICAL(IdeBundle.message("terminal.cursor.shape.vertical.name"))
  }

  class State {
    var cursorShape: CursorShape = CursorShape.BLOCK
    var maxVisibleCompletionItemsCount: Int = 6
    var autoShowDocumentationPopup: Boolean = true
  }

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }
}