/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.IdeFrame

abstract class CloseNotificationAction : AnAction() {
  override fun update(e: AnActionEvent) {
    val layout = getBalloonLayout(e)
    e.presentation.isEnabled = layout != null && layout.balloonCount > 0
  }
}

class CloseFirstNotificationAction : CloseNotificationAction() {
  override fun actionPerformed(e: AnActionEvent) {
    getBalloonLayout(e)!!.closeFirst()
  }
}

class CloseAllNotificationsAction : CloseNotificationAction() {
  override fun actionPerformed(e: AnActionEvent) {
    getBalloonLayout(e)!!.closeAll()
  }
}

private fun getBalloonLayout(e: AnActionEvent) = e.getData(IdeFrame.KEY)?.balloonLayout as BalloonLayoutImpl?
