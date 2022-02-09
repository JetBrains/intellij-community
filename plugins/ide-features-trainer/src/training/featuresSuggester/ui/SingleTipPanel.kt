package training.featuresSuggester.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipUIUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

@Suppress("MagicNumber")
class SingleTipPanel(tip: TipAndTrickBean) : JPanel() {
  companion object {
    private val DIVIDER_COLOR = JBColor(0xd9d9d9, 0x515151)
    private const val DEFAULT_WIDTH = 400
    private const val DEFAULT_HEIGHT = 200
    private const val LAST_SEEN_TIP_ID = "lastSeenTip"
  }

  private val browser: TipUIUtil.Browser
  private val poweredByLabel: JLabel

  init {
    layout = BorderLayout()
    if (SystemInfo.isWin10OrNewer && !StartupUiUtil.isUnderDarcula()) {
      border = JBUI.Borders.customLine(Gray.xD0, 1, 0, 0, 0)
    }
    browser = TipUIUtil.createBrowser()
    browser.component.border = JBUI.Borders.empty(8, 12)
    val scrollPane = ScrollPaneFactory.createScrollPane(browser.component, true)
    scrollPane.border = JBUI.Borders.customLine(DIVIDER_COLOR, 0, 0, 1, 0)
    add(scrollPane, BorderLayout.CENTER)

    poweredByLabel = JBLabel().apply {
      border = JBUI.Borders.empty(0, 10)
      foreground = SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.fgColor
    }
    add(poweredByLabel, BorderLayout.SOUTH)

    setTip(tip)
  }

  private fun setTip(tip: TipAndTrickBean) {
    PropertiesComponent.getInstance().setValue(LAST_SEEN_TIP_ID, tip.fileName)
    TipUIUtil.openTipInBrowser(tip, browser)
    poweredByLabel.text = TipUIUtil.getPoweredByText(tip)
    poweredByLabel.isVisible = !StringUtil.isEmpty(poweredByLabel.text)
  }

  override fun getPreferredSize(): Dimension = JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT)
}
