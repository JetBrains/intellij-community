// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JPanel

internal class GHPRHeaderPanel(private val model: SingleValueModel<GHPullRequestShort>)
  : JPanel(), Disposable {
  private val title = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
    font = font.deriveFont((font.size * 1.5).toFloat())
  }

  private val number = JBLabel(UIUtil.ComponentStyle.LARGE).apply {
    font = font.deriveFont((font.size * 1.3).toFloat())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP / 2)
  }

  init {
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill())

    isOpaque = false
    add(title, CC())
    add(number, CC().growX().pushX().alignX("left"))

    fun update() {
      title.text = model.value.title
      number.text = "#" + model.value.number
    }

    model.addValueChangedListener(this) {
      update()
    }
    update()
  }

  override fun dispose() {}
}