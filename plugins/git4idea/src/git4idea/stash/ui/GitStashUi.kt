// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.BorderLayout
import javax.swing.JPanel

class GitStashUi(project: Project, disposable: Disposable) : Disposable {
  val mainComponent: JPanel = JPanel(BorderLayout())

  init {
    Disposer.register(disposable, this)
  }

  override fun dispose() {
  }
}