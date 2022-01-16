// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.GridBag
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel

class MainToolbar(project: Project?): JPanel(GridBagLayout()), Disposable {

  init {
    val projectWidget = ProjectWidget(project)
    Disposer.register(this, projectWidget)

    val gb = GridBag().nextLine()
    add(projectWidget, gb.next().fillCellNone().weightx(1.0).anchor(GridBagConstraints.CENTER))
  }

  override fun dispose() {}
}