/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.colorpicker

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private val PANEL_BORDER = JBUI.Borders.empty(0, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)

private val PREFERRED_PANEL_SIZE = JBUI.size(PICKER_PREFERRED_WIDTH, 50)

private const val TEXT_FIELDS_UPDATING_DELAY = 300

private val COLOR_RANGE = 0..255
private val HUE_RANGE = 0..360
private val PERCENT_RANGE = 0..100

enum class AlphaFormat {
  BYTE,
  PERCENTAGE;

  fun next() : AlphaFormat = when (this) {
    BYTE -> PERCENTAGE
    PERCENTAGE -> BYTE
  }
}

enum class ColorFormat {
  RGB,
  HSB;

  fun next() : ColorFormat = when (this) {
    RGB -> HSB
    HSB -> RGB
  }
}

class ColorValuePanel(private val model: ColorPickerModel, private val showAlpha: Boolean = false, private val showAlphaInPercent: Boolean = true)
  : JPanel(GridBagLayout()), DocumentListener, ColorListener {

  /**
   * Used to update the color of picker when color text fields are edited.
   */
  @get:TestOnly
  val updateAlarm = Alarm()

  @get:TestOnly
  val alphaField = ColorValueField()
  private val alphaHexDocument = DigitColorDocument(alphaField, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val alphaPercentageDocument = DigitColorDocument(alphaField, PERCENT_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  @get:TestOnly
  val hexField = ColorValueField(hex = true, showAlpha = showAlpha)

  private val alphaLabel = ColorLabel()
  private val colorLabel1 = ColorLabel()
  private val colorLabel2 = ColorLabel()
  private val colorLabel3 = ColorLabel()

  @TestOnly
  val alphaButtonPanel = createAlphaLabel(alphaLabel) {
    currentAlphaFormat = currentAlphaFormat.next()
  }

  @TestOnly
  val colorFormatButtonPanel = createFormatLabels(colorLabel1, colorLabel2, colorLabel3) {
    currentColorFormat = currentColorFormat.next()
  }

  @get:TestOnly
  val colorField1 = ColorValueField()
  private val redDocument = DigitColorDocument(colorField1, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val hueDocument = DigitColorDocument(colorField1, HUE_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  @get:TestOnly
  val colorField2 = ColorValueField()
  private val greenDocument = DigitColorDocument(colorField2, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val saturationDocument = DigitColorDocument(colorField2, PERCENT_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  @get:TestOnly
  val colorField3 = ColorValueField()
  private val blueDocument = DigitColorDocument(colorField3, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val brightnessDocument = DigitColorDocument(colorField3, PERCENT_RANGE).apply { addDocumentListener(this@ColorValuePanel) }

  private var currentAlphaFormat by Delegates.observable(if (showAlphaInPercent) AlphaFormat.PERCENTAGE else loadAlphaFormatProperty()) { _, _, newValue ->
    updateAlphaFormat()
    saveAlphaFormatProperty(newValue)
    repaint()
  }

  private var currentColorFormat by Delegates.observable(loadColorFormatProperty()) { _, _, newValue ->
    updateColorFormat()
    saveColorFormatProperty(newValue)
    repaint()
  }

  init {
    border = PANEL_BORDER
    preferredSize = PREFERRED_PANEL_SIZE
    background = PICKER_BACKGROUND_COLOR
    isFocusable = false

    val c = GridBagConstraints()
    c.fill = GridBagConstraints.HORIZONTAL

    c.weightx = 0.36
    c.gridwidth = 3
    c.gridx = 1
    c.gridy = 0
    add(colorFormatButtonPanel, c)

    c.gridwidth = 1
    c.weightx = 0.12
    c.gridx = 1
    c.gridy = 1
    add(colorField1, c)
    c.gridx = 2
    c.gridy = 1
    add(colorField2, c)
    c.gridx = 3
    c.gridy = 1
    add(colorField3, c)

    if (showAlpha) {
      c.weightx = 0.12
      c.gridx = 4
      c.gridy = 0
      add(alphaButtonPanel, c)
      c.gridy = 1
      add(alphaField, c)
    }

    // Hex should be longer
    c.gridheight = 1
    c.weightx = 0.51
    c.gridx = if(showAlpha) 5 else  4
    c.gridy = 0
    add(ColorLabel(IdeBundle.message("colorpicker.colorvaluepanel.hexlabel")), c)
    c.gridy = 1
    add(hexField, c)
    hexField.document = HexColorDocument(hexField)
    hexField.document.addDocumentListener(this)

    updateAlphaFormat()
    updateColorFormat()

    model.addListener(this)
  }

  override fun requestFocusInWindow() = colorField1.requestFocusInWindow()

  private fun updateAlphaFormat() {
    when (currentAlphaFormat) {
      AlphaFormat.BYTE -> {
        alphaLabel.text = IdeBundle.message("colorpanel.label.alpha")
        alphaField.document = alphaHexDocument
        alphaField.text = model.alpha.toString()
      }
      AlphaFormat.PERCENTAGE -> {
        alphaLabel.text = IdeBundle.message("colorpanel.label.alpha.percent")
        alphaField.document = alphaPercentageDocument
        alphaField.text = (model.alpha * 100f / 0xFF).roundToInt().toString()
      }
    }
    // change the text in document trigger the listener, but it doesn't to update the color in Model in this case.
    updateAlarm.cancelAllRequests()
    repaint()
  }

  private fun updateColorFormat() {
    when (currentColorFormat) {
      ColorFormat.RGB -> {
        colorLabel1.text = IdeBundle.message("colorpanel.label.red")
        colorLabel2.text = IdeBundle.message("colorpanel.label.green")
        colorLabel3.text = IdeBundle.message("colorpanel.label.blue")

        colorField1.document = redDocument
        colorField2.document = greenDocument
        colorField3.document = blueDocument

        colorField1.text = model.red.toString()
        colorField2.text = model.green.toString()
        colorField3.text = model.blue.toString()
      }
      ColorFormat.HSB -> {
        colorLabel1.text = IdeBundle.message("colorpanel.label.hue")
        colorLabel2.text = IdeBundle.message("colorpanel.label.saturation")
        colorLabel3.text = IdeBundle.message("colorpanel.label.brightness")

        colorField1.document = hueDocument
        colorField2.document = saturationDocument
        colorField3.document = brightnessDocument

        colorField1.text = (model.hue * 360).roundToInt().toString()
        colorField2.text = (model.saturation * 100).roundToInt().toString()
        colorField3.text = (model.brightness * 100).roundToInt().toString()
      }
    }
    // change the text in document trigger the listener, but it doesn't to update the color in Model in this case.
    updateAlarm.cancelAllRequests()
    repaint()
  }

  override fun colorChanged(color: Color, source: Any?) = updateTextField(color, source)

  private fun updateTextField(color: Color, source: Any?) {
    if (currentAlphaFormat == AlphaFormat.BYTE) {
      alphaField.setTextIfNeeded(color.alpha.toString(), source)
    }
    else {
      alphaField.setTextIfNeeded((color.alpha * 100f / 0xFF).roundToInt().toString(), source)
    }
    if (currentColorFormat == ColorFormat.RGB) {
      colorField1.setTextIfNeeded(color.red.toString(), source)
      colorField2.setTextIfNeeded(color.green.toString(), source)
      colorField3.setTextIfNeeded(color.blue.toString(), source)
    }
    else {
      val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
      colorField1.setTextIfNeeded((hsb[0] * 360).roundToInt().toString(), source)
      colorField2.setTextIfNeeded((hsb[1] * 100).roundToInt().toString(), source)
      colorField3.setTextIfNeeded((hsb[2] * 100).roundToInt().toString(), source)
    }
    var hexStr = String.format("%02X", color.red) + String.format("%02X", color.green) + String.format("%02X", color.blue)
    if (showAlpha) {
      hexStr += String.format("%02X", color.alpha)
    }
    hexField.setTextIfNeeded(hexStr, source)
    // Cleanup the update requests which triggered by setting text in this function
    updateAlarm.cancelAllRequests()
  }

  private fun JTextField.setTextIfNeeded(newText: String?, source: Any?) {
    if (text != newText && (source != this@ColorValuePanel || !isFocusOwner)) {
      text = newText
    }
  }

  override fun insertUpdate(e: DocumentEvent) = update((e.document as ColorDocument).src)

  override fun removeUpdate(e: DocumentEvent) = update((e.document as ColorDocument).src)

  override fun changedUpdate(e: DocumentEvent) = Unit

  private fun update(src: JTextField) {
    updateAlarm.cancelAllRequests()
    updateAlarm.addRequest({ updateColorToColorModel(src) }, TEXT_FIELDS_UPDATING_DELAY)
  }

  private fun updateColorToColorModel(src: JTextField?) {
    val color = if (src == hexField) {
      convertHexToColor(hexField.text)
    }
    else {
      val a = if (currentAlphaFormat == AlphaFormat.BYTE) {
        if(showAlpha) alphaField.colorValue else 255
      }
      else {
        if (showAlpha) (alphaField.colorValue * 0xFF / 100f).roundToInt() else 100
      }
      when (currentColorFormat) {
        ColorFormat.RGB -> {
          val r = colorField1.colorValue
          val g = colorField2.colorValue
          val b = colorField3.colorValue
          if (showAlpha) Color(r, g, b, a) else Color(r, g, b)
        }
        ColorFormat.HSB -> {
          val h = colorField1.colorValue / 360f
          val s = colorField2.colorValue / 100f
          val b = colorField3.colorValue / 100f
          Color((a shl 24) or (0x00FFFFFF and Color.HSBtoRGB(h, s, b)), showAlpha)
        }
      }
    }
    model.setColor(color, this)
  }

  companion object {
    private fun createAlphaLabel(alphaLabel: ColorLabel, onClick: () -> Unit) = object : ButtonPanel() {

      init {
        layout = GridLayout(1, 1)
        add(alphaLabel)
      }

      override fun clicked() {
        onClick.invoke()
      }
    }

    private fun createFormatLabels(label1: ColorLabel,
                                   label2: ColorLabel,
                                   label3: ColorLabel,
                                   onClick: () -> Unit) = object : ButtonPanel() {

      init {
        layout = GridLayout(1, 3)
        add(label1)
        add(label2)
        add(label3)
      }

      override fun clicked() {
        onClick.invoke()
      }
    }
  }
}

private const val HOVER_BORDER_LEFT = 0
private const val HOVER_BORDER_TOP = 0
private const val HOVER_BORDER_WIDTH = 1
private val HOVER_BORDER_STROKE = BasicStroke(1f)
private val HOVER_BORDER_COLOR = Color.GRAY.brighter()

private const val PRESSED_BORDER_LEFT = 1
private const val PRESSED_BORDER_TOP = 1
private const val PRESSED_BORDER_WIDTH = 2
private val PRESSED_BORDER_STROKE = BasicStroke(1.2f)
private val PRESSED_BORDER_COLOR = Color.GRAY

private const val BORDER_CORNER_ARC = 7

private const val ACTION_PRESS_BUTTON_PANEL = "pressButtonPanel"
private const val ACTION_RELEASE_BUTTON_PANEL = "releaseButtonPanel"

abstract class ButtonPanel : JPanel() {

  companion object {
    private enum class Status { NORMAL, HOVER, PRESSED }
  }

  private var mouseStatus by Delegates.observable(Status.NORMAL) { _, _, _ ->
    repaint()
  }

  private val mouseAdapter = object : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent?) = clicked()

    override fun mouseEntered(e: MouseEvent?) {
      if (!isFocusOwner) {
        mouseStatus = Status.HOVER
      }
    }

    override fun mouseExited(e: MouseEvent?) {
      if (!isFocusOwner) {
        mouseStatus = Status.NORMAL
      }
    }

    override fun mousePressed(e: MouseEvent?) {
      if (!isFocusOwner) {
        mouseStatus = Status.PRESSED
      }
    }

    override fun mouseReleased(e: MouseEvent?) {
      if (!isFocusOwner) {
        mouseStatus = if (mouseStatus == Status.PRESSED) Status.HOVER else Status.NORMAL
      }
    }
  }

  private val focusAdapter = object : FocusAdapter() {

    override fun focusGained(e: FocusEvent?) {
      mouseStatus = Status.HOVER
    }

    override fun focusLost(e: FocusEvent?) {
      mouseStatus = Status.NORMAL
    }
  }

  init {
    border = BorderFactory.createEmptyBorder()
    background = PICKER_BACKGROUND_COLOR
    addMouseListener(mouseAdapter)
    addFocusListener(focusAdapter)

    with (getInputMap(JComponent.WHEN_FOCUSED)) {
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), ACTION_PRESS_BUTTON_PANEL)
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), ACTION_RELEASE_BUTTON_PANEL)
    }

    with (actionMap) {
      put(ACTION_PRESS_BUTTON_PANEL, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          mouseStatus = Status.PRESSED
        }
      })

      put(ACTION_RELEASE_BUTTON_PANEL, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          mouseStatus = Status.HOVER
          clicked()
        }
      })
    }
  }

  // Needs to be final to be used in init block
  final override fun addMouseListener(l: MouseListener?) = super.addMouseListener(l)

  // Needs to be final to be used in init block
  final override fun addFocusListener(l: FocusListener?) = super.addFocusListener(l)

  override fun isFocusable() = true

  abstract fun clicked()

  override fun paintBorder(g: Graphics) {
    if (g !is Graphics2D) return
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val originalStroke = g.stroke
    when (mouseStatus) {
      Status.HOVER -> {
        g.stroke = HOVER_BORDER_STROKE
        g.color = HOVER_BORDER_COLOR
        g.drawRoundRect(HOVER_BORDER_LEFT,HOVER_BORDER_TOP,
                        width - HOVER_BORDER_WIDTH, height - HOVER_BORDER_WIDTH,
                        BORDER_CORNER_ARC, BORDER_CORNER_ARC)
      }
      Status.PRESSED -> {
        g.stroke = PRESSED_BORDER_STROKE
        g.color = PRESSED_BORDER_COLOR
        g.drawRoundRect(PRESSED_BORDER_LEFT, PRESSED_BORDER_TOP,
                        width - PRESSED_BORDER_WIDTH, height - PRESSED_BORDER_WIDTH,
                        BORDER_CORNER_ARC, BORDER_CORNER_ARC)
      }
      else -> return
    }
    g.stroke = originalStroke
  }
}

private class ColorLabel(@NlsContexts.Label text: String = ""): JLabel(text, SwingConstants.CENTER) {
  init {
    foreground = PICKER_TEXT_COLOR
  }
}

private const val ACTION_UP = "up"
private const val ACTION_UP_FAST = "up_fast"
private const val ACTION_DOWN = "down"
private const val ACTION_DOWN_FAST = "down_fast"

class ColorValueField(private val hex: Boolean = false, private val showAlpha: Boolean = false): JTextField(fieldLength(hex, showAlpha)) {

  init {
    horizontalAlignment = JTextField.CENTER
    isEnabled = true
    isEditable = true

    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        selectAll()
      }

      override fun focusLost(e: FocusEvent?) {
        val size = document?.length ?: return
        selectionStart = size
        selectionEnd = size
      }
    })
    addMouseWheelListener { e -> increaseValue((-e.preciseWheelRotation).toInt()) }
    if (!hex) {
      // Don't increase value for hex field.
      with(getInputMap(JComponent.WHEN_FOCUSED)) {
        put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_UP)
        put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.SHIFT_DOWN_MASK), ACTION_UP_FAST)
        put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_DOWN)
        put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.SHIFT_DOWN_MASK), ACTION_DOWN_FAST)
      }
      with(actionMap) {
        put(ACTION_UP, object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) = increaseValue(1)
        })
        put(ACTION_UP_FAST, object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) = increaseValue(10)
        })
        put(ACTION_DOWN, object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) = increaseValue(-1)
        })
        put(ACTION_DOWN_FAST, object : AbstractAction() {
          override fun actionPerformed(e: ActionEvent) = increaseValue(-10)
        })
      }
    }
  }

  private fun increaseValue(diff: Int) {
    assert(!hex)

    val doc = document as DigitColorDocument
    val newValue = doc.getText(0, doc.length).toInt() + diff
    val valueInRange = Math.max(doc.valueRange.start, Math.min(newValue, doc.valueRange.endInclusive))
    text = valueInRange.toString()
  }

  override fun isFocusable() = true

  val colorValue: Int
    get() {
      val rawText = text
      return if (rawText.isBlank()) 0 else Integer.parseInt(rawText, if (hex) 16 else 10)
    }
}

private fun fieldLength(hex: Boolean, showAlpha: Boolean) = if (hex && showAlpha) 8
                                                            else if (hex) 6 else 3

private abstract class ColorDocument(internal val src: JTextField) : PlainDocument() {

  override fun insertString(offs: Int, str: String, a: AttributeSet?) {
    val source = str.toCharArray()
    val selected = src.selectionEnd - src.selectionStart
    val newLen = src.text.length - selected + str.length
    if (this is HexColorDocument && selected == 0 && ColorUtil.fromHex(str, null) != null) {
      super.remove(0, src.text.length)
      super.insertString(0, StringUtil.trimStart(str, "#").uppercase(Locale.getDefault()), a)
      return
    }
    if (newLen > src.columns) {
      return
    }

    val charsToInsert = source
      .filter { isLegalCharacter(it) }
      .map { it.toUpperCase() }
      .joinToString("")

    val res = StringBuilder(src.text).insert(offs, charsToInsert).toString()
    if (!isLegalValue(res)) {
      return
    }
    super.insertString(offs, charsToInsert, a)
  }

  abstract fun isLegalCharacter(c: Char): Boolean

  abstract fun isLegalValue(str: String): Boolean
}

private class DigitColorDocument(src: JTextField, val valueRange: IntRange) : ColorDocument(src) {

  override fun isLegalCharacter(c: Char) = c.isDigit()

  override fun isLegalValue(str: String) = try { str.toInt() in valueRange } catch (_: NumberFormatException) { false }
}

private class HexColorDocument(src: JTextField) : ColorDocument(src) {

  override fun isLegalCharacter(c: Char) = StringUtil.isHexDigit(c)

  override fun isLegalValue(str: String) = true
}

private fun convertHexToColor(hex: String): Color {
  val s = if (hex == "") "0" else hex
  val i = s.toLong(16)
  if (hex.length > 6) {
    return Color(
      (i shr 24 and 0xFF).toInt(), //RED
      (i shr 16 and 0xFF).toInt(), //GREEN
      (i shr 8 and 0xFF).toInt(),  //BLUE
      (i and 0xFF).toInt()         //ALPHA
    )
  } else {
    return Color(
      (i shr 16 and 0xFF).toInt(), //RED
      (i shr 8 and 0xFF).toInt(),  //GREEN
      (i and 0xFF).toInt()         //BLUE
    )
  }
}

private const val PROPERTY_PREFIX = "colorValuePanel_"

private const val PROPERTY_NAME_ALPHA_FORMAT = PROPERTY_PREFIX + "alphaFormat"
private val DEFAULT_ALPHA_FORMAT = AlphaFormat.PERCENTAGE

private const val PROPERTY_NAME_COLOR_FORMAT = PROPERTY_PREFIX + "colorFormat"
private val DEFAULT_COLOR_FORMAT = ColorFormat.RGB

private fun loadAlphaFormatProperty(): AlphaFormat {
  val alphaFormatName = PropertiesComponent.getInstance().getValue(PROPERTY_NAME_ALPHA_FORMAT, DEFAULT_ALPHA_FORMAT.name)
  return try {
    AlphaFormat.valueOf(alphaFormatName)
  }
  catch (e: IllegalArgumentException) {
    DEFAULT_ALPHA_FORMAT
  }
}

private fun saveAlphaFormatProperty(alphaFormat: AlphaFormat) {
  PropertiesComponent.getInstance().setValue(PROPERTY_NAME_ALPHA_FORMAT, alphaFormat.name)
}

private fun loadColorFormatProperty(): ColorFormat {
  val colorFormatName = PropertiesComponent.getInstance().getValue(PROPERTY_NAME_COLOR_FORMAT, DEFAULT_COLOR_FORMAT.name)
  return try {
    ColorFormat.valueOf(colorFormatName)
  }
  catch (e: IllegalArgumentException) {
    DEFAULT_COLOR_FORMAT
  }
}

private fun saveColorFormatProperty(colorFormat: ColorFormat) {
  PropertiesComponent.getInstance().setValue(PROPERTY_NAME_COLOR_FORMAT, colorFormat.name)
}
