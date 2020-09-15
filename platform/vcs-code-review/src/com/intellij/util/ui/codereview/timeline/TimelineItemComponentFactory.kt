// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline

import javax.swing.JComponent

interface TimelineItemComponentFactory<in T : TimelineItem> {
  fun createComponent(item: T): JComponent
}