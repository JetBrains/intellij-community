// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class CertificateConfigurableUi(treeDecorator: ToolbarDecorator) {

  private val noCertificateDetails: JComponent = panel {
    row {
      label(IdeBundle.message("settings.certificate.no.certificate.selected"))
        .align(Align.CENTER)
    }.resizableRow()
  }

  private val splitter: Splitter = Splitter(true, 0.3f).apply {
    isShowDividerControls = false
    firstComponent = panel {
      row {
        cell(treeDecorator.createPanel())
          .label(IdeBundle.message("settings.certificate.accepted.certificates"), LabelPosition.TOP)
          .align(Align.FILL)
      }.resizableRow()
    }
    secondComponent = noCertificateDetails
  }

  @JvmField
  val panel = panel {
    row {
      // CertificateManager.getInstance().getState() can be replaced
      checkBox(IdeBundle.message("settings.certificate.accept.non.trusted.certificates.automatically"))
        .bindSelected({ CertificateManager.getInstance().getState().ACCEPT_AUTOMATICALLY },
                      { CertificateManager.getInstance().getState().ACCEPT_AUTOMATICALLY = it })
    }
    row {
      cell(splitter)
        .align(Align.FILL)
    }.resizableRow()
  }

  fun setDetails(details: JComponent?) {
    splitter.secondComponent = details ?: noCertificateDetails
  }
}
