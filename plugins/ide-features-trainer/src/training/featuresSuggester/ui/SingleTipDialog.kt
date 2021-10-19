package training.featuresSuggester.ui

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.ui.JBUI
import training.featuresSuggester.FeatureSuggesterBundle
import java.awt.Window
import javax.swing.JComponent

@Suppress("MagicNumber")
class SingleTipDialog(parent: Window, tip: TipAndTrickBean) : DialogWrapper(parent, true) {
  companion object {
    private const val LAST_TIME_TIPS_WERE_SHOWN = "lastTimeTipsWereShown"
    private var ourInstance: SingleTipDialog? = null

    fun showForProject(project: Project, tip: TipAndTrickBean) {
      ourInstance = createForProject(project, tip)
      ourInstance?.show()
    }

    private fun createForProject(project: Project, tip: TipAndTrickBean): SingleTipDialog? {
      val window = WindowManagerEx.getInstanceEx().suggestParentWindow(project)
                   ?: WindowManagerEx.getInstanceEx().findVisibleFrame()
                   ?: return null
      if (ourInstance != null && ourInstance!!.isVisible) {
        ourInstance!!.dispose()
      }
      return SingleTipDialog(window, tip)
    }
  }

  private val tipPanel: SingleTipPanel

  init {
    isModal = false
    title = FeatureSuggesterBundle.message("tip.title")
    setCancelButtonText(CommonBundle.getCloseButtonText())
    tipPanel = SingleTipPanel(tip)
    horizontalStretch = 1.33f
    verticalStretch = 1.25f
    init()
  }

  override fun show() {
    PropertiesComponent.getInstance()
      .setValue(LAST_TIME_TIPS_WERE_SHOWN, System.currentTimeMillis().toString())
    super.show()
  }

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  override fun createCenterPanel(): JComponent = tipPanel

  override fun createSouthPanel(): JComponent? {
    val component = super.createSouthPanel()
    component.border = JBUI.Borders.empty(8, 12)
    return component
  }
}
