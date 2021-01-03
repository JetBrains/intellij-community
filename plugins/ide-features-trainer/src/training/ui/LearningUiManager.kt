// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.project.Project
import com.intellij.util.containers.BidirectionalMap
import training.util.WeakReferenceDelegator
import javax.swing.Icon

object LearningUiManager {
  var learnProject: Project? by WeakReferenceDelegator()

  var activeToolWindow: LearnToolWindow? by WeakReferenceDelegator()

  val iconMap = BidirectionalMap<String, Icon>()

  fun resetModulesView() {
    activeToolWindow?.setModulesPanel()
    activeToolWindow = null
  }

  fun getIconIndex(icon: Icon): String {
    var index = iconMap.getKeysByValue(icon)?.firstOrNull()
    if (index == null) {
      index = iconMap.size.toString()
      iconMap[index] = icon
    }
    return index
  }
}