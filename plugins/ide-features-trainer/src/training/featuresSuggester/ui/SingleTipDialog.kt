package training.featuresSuggester.ui

import com.intellij.CommonBundle
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import training.featuresSuggester.FeatureSuggesterBundle
import javax.swing.JComponent

class SingleTipDialog(private val tip: TipAndTrickBean, private val project: Project) : DialogWrapper(project) {
  init {
    isModal = false
    title = FeatureSuggesterBundle.message("tip.title")
    setCancelButtonText(CommonBundle.getCloseButtonText())
    horizontalStretch = 1.33f
    verticalStretch = 1.25f
    init()
  }

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  override fun createCenterPanel(): JComponent {
    val panel = TipPanel(project, disposable)
    panel.setTips(listOf(tip))
    return panel
  }

  override fun createSouthPanel(): JComponent? {
    val component = super.createSouthPanel()
    component.border = JBUI.Borders.empty(8, 12)
    return component
  }
}
