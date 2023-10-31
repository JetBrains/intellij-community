package com.intellij.ide.startup.importSettings.chooser.importProgress

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.BannerOverlay
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.ImportFromProduct
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.minimumWidth
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.Lifetime
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagLayout
import javax.swing.*

class ImportProgressDialog(importFromProduct: DialogImportData): PageProvider(false) {
  override fun showExit(): MessageDialogBuilder.YesNo = MessageDialogBuilder.yesNo(ImportSettingsBundle.message("exit.confirm.title"),
                                                                                   ImportSettingsBundle.message("exit.confirm.prompt"))
    .yesText(ImportSettingsBundle.message("stop.import"))
    .noText(CommonBundle.getCancelButtonText())


  private val panel = JPanel(VerticalLayout(JBUI.scale(8))).apply {
    add(JPanel(VerticalLayout(JBUI.scale(8))).apply {
      add(JLabel(ImportSettingsBundle.message("import.settings.title")).apply {
        font = Font(font.fontName, Font.PLAIN, JBUIScale.scaleFontSize(24f))
        horizontalAlignment = SwingConstants.CENTER
      })

      importFromProduct.message?.let {
        add(JLabel(it).apply {
          horizontalAlignment = SwingConstants.CENTER
        })
      }

      isOpaque = false
      border = JBUI.Borders.empty(30, 0, 20, 0)
    })


    if(importFromProduct is ImportFromProduct) {
      val from = importFromProduct.from
      val to = importFromProduct.to

      add(JPanel(GridBagLayout()).apply {
        val cn = GridBagConstraints()
        cn.fill = HORIZONTAL

        cn.weightx = 1.0
        cn.gridx = 0
        cn.gridy = 0
        add(JLabel(from.icon), cn)

        cn.gridx = 1
        cn.gridy = 0
        cn.weightx = 0.0
        add(JLabel(AllIcons.Chooser.Right), cn)

        cn.weightx = 1.0
        cn.gridx = 2
        cn.gridy = 0
        add(JLabel(to.icon), cn)

        cn.gridx = 0
        cn.gridy = 1
        add(HLabel(from.item.name).label, cn)

        cn.gridx = 2
        cn.gridy = 1
        add(HLabel(to.item.name).label, cn)

        border = JBUI.Borders.emptyBottom(18)

      })
    }

    add(JPanel(VerticalLayout(JBUI.scale(8)).apply {
      add(JProgressBar(0, 99).apply {
        importFromProduct.progress.progress.advise(Lifetime.Eternal) {
          this.value = it
        }
        preferredWidth = JBUI.scale(280)
      })

      val hLabel = CommentLabel("")
      importFromProduct.progress.progressMessage.advise(Lifetime.Eternal) {
        hLabel.text = if (it != null) "<center>$it</center>" else "&nbsp"
      }

      add(hLabel.label.apply {
        font = JBFont.medium()
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      })
    }).apply {
      isOpaque = false
      border = JBUI.Borders.emptyTop(20)
    })

    border = JBUI.Borders.empty()
  }

  private val overlay = BannerOverlay()

  init {
    val settService = SettingsService.getInstance()
    settService.error.advise(disposable.createLifetime()) {
      overlay.showError(it)
    }

    val lifetime = disposable.createLifetime()
    settService.doClose.advise(lifetime) {
      doClose()
    }
  }

  override fun createContent(): JComponent {
    val comp = JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 442)
      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      add(panel, gbc)
      border = JBUI.Borders.empty()
    }

    return overlay.wrapComponent(comp)
  }

  override fun createActions(): Array<Action> {
    return arrayOf()
  }

  private open class HLabel(txt: String) {
    var text: @NlsContexts.Label String = ""
      set(value) {
        if (field == value) return
        lbl.text = "<html><center>${value}</center></html>"
        field = value
      }

    private val lbl = object : JLabel() {
      override fun getPreferredSize(): Dimension {
        val preferredSize = super.getPreferredSize()
        return getPref(preferredSize.height)
      }
    }

    protected open fun getPref(prefH: Int): Dimension {
      return Dimension(0, prefH)
    }

    val label: JComponent
      get() {
        return lbl
      }

    init {
      lbl.isOpaque = false
      lbl.minimumWidth = 10
      lbl.horizontalAlignment = SwingConstants.CENTER
      lbl.verticalAlignment = SwingConstants.TOP
      text = txt
    }
  }

  private class CommentLabel(txt: String) : HLabel(txt) {
    override fun getPref(prefH: Int): Dimension {
      return Dimension(0, Math.max(prefH, JBUI.scale(45)))
    }
  }
}