// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GHPRReviewThreadModel
import javax.swing.JComponent

interface GHPREditorReviewThreadComponentFactory {
  fun createComponent(thread: GHPRReviewThreadModel): JComponent
}