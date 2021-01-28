// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.project.Project
import com.intellij.util.containers.BidirectionalMap
import training.learn.lesson.LessonManager
import training.util.WeakReferenceDelegator
import javax.swing.Icon

object LearningUiManager {
  var learnProject: Project? by WeakReferenceDelegator()

  private var activeToolWindowWeakRef: LearnToolWindow? by WeakReferenceDelegator()

  var activeToolWindow: LearnToolWindow?
    get() {
      val res = activeToolWindowWeakRef
      if (res != null && res.project.isDisposed) {
        activeToolWindowWeakRef = null
        return null
      }
      return res
    }
    set(value) {
      activeToolWindowWeakRef = value
    }

  val iconMap = BidirectionalMap<String, Icon>()

  fun resetModulesView() {
    activeToolWindow?.setModulesPanel()
    activeToolWindow = null
    LessonManager.instance.stopLesson()
    LessonManager.instance.clearCurrentLesson()
  }

  fun getIconIndex(icon: Icon): String {
    var index = iconMap.getKeysByValue(icon)?.firstOrNull()
    if (index == null) {
      index = iconMap.size.toString()
      iconMap[index] = icon
    }
    return index
  }

  private val callbackMap = mutableMapOf<Int, () -> Unit>()
  private var currentCallbackId = 0

  /** The returned Id should be used in the text only once */
  fun addCallback(callback: () -> Unit) : Int {
    callbackMap[currentCallbackId++] = callback
    return currentCallbackId - 1
  }

  fun getAndClearCallback(id: Int): (() -> Unit)? {
    val result = callbackMap[id]
    callbackMap.remove(id)
    return result
  }
}