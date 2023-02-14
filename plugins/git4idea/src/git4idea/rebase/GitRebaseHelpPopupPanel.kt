// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.StartupUiUtil
import git4idea.i18n.GitBundle
import java.awt.BorderLayout
import java.awt.Image
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GitRebaseHelpPopupPanel : JPanel() {

  val helpLink = createHelpLink()

  private val rebaseBranchImage = createImageComponent(REBASE_BRANCH_IMG)

  private val content = createContent()

  init {
    add(content, BorderLayout.CENTER)
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode == KeyEvent.VK_SPACE) {
          helpLink.doClick()
        }
      }
    })
  }

  private fun createContent(): JPanel = panel {
    row {
      label(GitBundle.message("rebase.help.rebase.branch"))
    }
    row {
      cell(rebaseBranchImage)
    }
    row {
      cell(helpLink)
    }
  }

  private fun createHelpLink() = BrowserLink(GitBundle.message("rebase.help.link"),
                                             "https://git-scm.com/docs/git-rebase")

  private fun createImageComponent(imagePath: String): JComponent {
    val suitableImagePath = chooseImage(imagePath)
    val image = loadImage(suitableImagePath)
    return JLabel(image?.let(::JBImageIcon))
  }

  private fun loadImage(path: String): Image? {
    return try {
      val img = ImageIO.read(javaClass.getResourceAsStream(path))

      JBHiDPIScaledImage(img, 274, 140, img.type)
    }
    catch (e: Exception) {
      LOG.warn("Failed to load image: ${path}", e)
      null
    }
  }

  private fun chooseImage(imagePath: String): String {
    val themePart = if (StartupUiUtil.isUnderDarcula()) DARK_POSTFIX else ""
    val retinaPart = if (StartupUiUtil.isJreHiDPI()) HIDPI_POSTFIX else ""

    return "${imagePath}${themePart}${retinaPart}.png"
  }

  companion object {
    private val LOG = logger<GitRebaseHelpPopupPanel>()

    private const val REBASE_BRANCH_IMG = "/images/rebase-branch"

    private const val DARK_POSTFIX = "-dark"
    private const val HIDPI_POSTFIX = "@2x"
  }
}