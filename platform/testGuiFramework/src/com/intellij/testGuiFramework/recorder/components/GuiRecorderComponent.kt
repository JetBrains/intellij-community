/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.recorder.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.testGuiFramework.recorder.ui.GuiScriptEditorFrame
import java.util.concurrent.Future

/**
 * @author Sergey Karashevich
 */
object GuiRecorderComponent : ApplicationComponent, Disposable {

  enum class States {IDLE, COMPILING, COMPILATION_ERROR, COMPILATION_DONE, RUNNING, RUNNING_ERROR, TEST_INIT }

  var myState: States = States.IDLE;

  override fun dispose() {
  }

  private var myFrame: GuiScriptEditorFrame? = null

  private var currentTask: Future<*>? = null

  override fun getComponentName() = "GuiRecorderComponent"

  fun setCurrentTask(task: Future<*>) { currentTask = task }

  fun cancelCurrentTask() {
    if (currentTask != null && !currentTask!!.isDone) currentTask!!.cancel(true)
  }

  fun getState() = myState

  fun setState(yaState: States) {
    myState = yaState
  }

  fun getFrame() = myFrame

  fun getEditor() = myFrame!!.getGuiScriptEditorPanel().editor

  fun registerFrame(frame: GuiScriptEditorFrame) {
    myFrame = frame
  }

  fun unregisterFrame() {
    if (myFrame != null)
      myFrame!!.dispose()
  }

  fun disposeFrame() {
    myFrame = null
  }

}