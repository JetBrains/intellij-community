// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.layout.*
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.StartupUiUtil
import git4idea.i18n.GitBundle
import java.awt.BorderLayout
import java.awt.Image
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GitRebaseHelpPopupPanel : JPanel() {

  val helpLink = createHelpLink()

  private val rebaseBranchImage = createImageComponent(REBASE_BRANCH_IMG)
  private val rebaseBranchPartImage = createImageComponent(REBASE_BRANCH_PART_IMG)

  private val content = createContent()

  init {
    add(content, BorderLayout.CENTER)
  }

  private fun createContent(): JPanel = panel {
    row {
      label(GitBundle.message("rebase.help.rebase.branch"))
    }
    row {
      rebaseBranchImage()
    }
    row {
      label(GitBundle.message("rebase.help.rebase.branch.part"))
    }
    row {
      rebaseBranchPartImage()
    }
    row {
      helpLink()
    }
  }

  private fun createHelpLink() = HyperlinkLabel().apply {
    setHyperlinkText(GitBundle.message("rebase.help.link"))
    setHyperlinkTarget("https://git-scm.com/docs/git-rebase")
  }

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
    private const val REBASE_BRANCH_PART_IMG = "/images/rebase-branch-part"

    private const val DARK_POSTFIX = "-dark"
    private const val HIDPI_POSTFIX = "@2x"
  }
}