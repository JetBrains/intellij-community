// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider

import com.intellij.dvcs.ui.DvcsCloneDialogComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import org.zmlx.hg4idea.HgRememberedInputs
import org.zmlx.hg4idea.util.HgUtil
import java.nio.file.Paths

class HgCloneDialogComponent(project: Project) : DvcsCloneDialogComponent(project,
                                                                          HgUtil.DOT_HG,
                                                                          HgRememberedInputs.getInstance()) {
  override fun doClone(project: Project, listener: CheckoutProvider.Listener) {
    val directory = Paths.get(getDirectory()).fileName.toString()
    val url = getUrl()
    val parent = Paths.get(getDirectory()).toAbsolutePath().parent.toAbsolutePath().toString()

    HgCheckoutProvider.doClone(project, listener, directory, url, parent)
  }
}