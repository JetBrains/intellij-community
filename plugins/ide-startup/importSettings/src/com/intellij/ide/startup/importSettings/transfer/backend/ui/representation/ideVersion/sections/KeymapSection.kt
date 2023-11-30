// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.startup.importSettings.models.*
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.border.CompoundBorder

class KeymapSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settingsCache.preferences, SettingsPreferencesKind.Keymap, AllIcons.TransferSettings.Keymap) {
  companion object {
    @Nls
    private val delim = if (SystemInfo.isMac) "" else "+"
    private const val DELIM_TO_PARSE = "+"

    private fun getKeystrokeText(accelerator: KeyStroke?): String {
      if (accelerator == null) return ""

      return if (SystemInfo.isMac)
        MacKeymapUtil.getKeyStrokeText(accelerator, DELIM_TO_PARSE, true)
      else KeymapUtil.getKeystrokeText(accelerator)
    }

    private fun init(sc: KeyboardShortcut): Pair<List<String>, List<String>?> {
      return Pair(getKeystrokeText(sc.firstKeyStroke).split(DELIM_TO_PARSE),
                  sc.secondKeyStroke?.let { getKeystrokeText(it).split(DELIM_TO_PARSE) })
    }

    private fun init(sc: DummyKeyboardShortcut): Pair<List<String>, List<String>?> {
      return Pair(sc.firstKeyStroke.split(DELIM_TO_PARSE), sc.secondKeyStroke?.split(DELIM_TO_PARSE))
    }
  }

  override val name: String = "Keymap"
  override val disabledCheckboxText: String = "Default IntelliJ keymap will be used"

  override fun worthShowing(): Boolean = ideVersion.settingsCache.keymap != null

  override fun getPopupHeight() = 250

  override fun getContent(): JComponent {
    val keymap = ideVersion.settingsCache.keymap ?: error("Keymap is null, this is very wrong")

    val customShortcuts = (keymap as? PatchedKeymap)?.overrides

    if (!customShortcuts.isNullOrEmpty()) {
      withMoreLabel(IdeBundle.message("transfer-settings.keymap.more")) {
        BorderLayoutPanel().apply {
          border = JBUI.Borders.empty()
          addToTop(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(IdeBundle.message("transfer-settings.keymap.custom-shortcuts")).apply {
              border = JBUI.Borders.empty(5, 5, 0, 5)
            })
            add(JLabel(IdeBundle.message("transfer-settings.keymap.extension-custom-shortcuts", ideVersion.name)).apply {
              border = JBUI.Borders.empty(0, 5)
              font = JBFont.small()
              foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
          })

          val s = createCustomShortcutsPanel(customShortcuts).apply {
            border = JBUI.Borders.empty(5)
          }

          addToCenter(JBScrollPane(s).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()
          })
        }
      }
    }

    return panel {
      val items = keymap.demoShortcuts.take(LIMIT)
      items.forEach {
        row {
          val dsc = it.defaultShortcut
          shortcutComp(dsc)
          mutableLabel(it.humanName).customize(UnscaledGaps(left = 10))
        }.customize(UnscaledGapsY(top = 10)).layout(RowLayout.PARENT_GRID)
      }
    }
  }

  private fun createCustomShortcutsPanel(customShortcuts: List<KeyBinding>) = panel {
    for (customShortcut in customShortcuts) {
      row {
        label(customShortcut.originalId ?: customShortcut.actionId)
      }
      row {
        panel {
          for (sc in customShortcut.shortcuts) {
            row {
              shortcutComp(sc)
            }
          }
        }
      }
    }
  }

  private fun Row.shortcutComp(dsc: Any) {
    if (dsc is KeyboardShortcut) cell(KeyboardTwoShortcuts(dsc, _isSelected, _isEnabled)).customize(UnscaledGaps.EMPTY)
    if (dsc is DummyKeyboardShortcut) cell(KeyboardTwoShortcuts(dsc, _isSelected, _isEnabled)).customize(UnscaledGaps.EMPTY)
  }

  private inner class KeyboardTwoShortcuts private constructor(shortcut: Pair<List<String>, List<String>?>, private val isSelected: AtomicBooleanProperty, private val isEnabledPanel: AtomicBooleanProperty) : JPanel() {
    init {
      layout = MigLayout("novisualpadding, ins 0, gap 0")
      parsePart(shortcut.first)
      shortcut.second?.let {
        add(JLabel(","), "gapleft 4, gapright 4")
        parsePart(it)
      }
    }

    constructor(sc: KeyboardShortcut, isSelected: AtomicBooleanProperty, isEnabled: AtomicBooleanProperty) : this(init(sc), isSelected, isEnabled)
    constructor(sc: DummyKeyboardShortcut, isSelected: AtomicBooleanProperty, isEnabled: AtomicBooleanProperty) : this(init(sc), isSelected, isEnabled)

    private fun parsePart(part: List<String>) {
      part.forEachIndexed { i, sc ->
        add(getKeyLabel(sc.trim()))
        if (i != part.size-1) {
          add(mutableJLabel(delim), "gapleft 4, gapright 4")
        }
      }
    }

    private fun getKeyLabel(txt: String): JLabel {
      return JLabel(txt).apply { // NON-NLS
        font = font.deriveFont(11.0f)
        border = CompoundBorder(
          RoundedLineBorder(JBColor.border(), 6, 1),
          JBUI.Borders.empty(2, 8)
        )

        isSelected.afterChange {
          foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
        }

        isEnabledPanel.afterChange {
          foreground = if (it || !isSelected.get()) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
        }
      }
    }
  }
}

