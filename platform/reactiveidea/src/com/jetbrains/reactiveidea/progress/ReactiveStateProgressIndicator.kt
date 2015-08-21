/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea.progress

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactiveidea.toModel
import com.jetbrains.reactivemodel.VariableSignal
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.reaction
import com.jetbrains.reactivemodel.util.Lifetime

class ReactiveStateProgressIndicator(life: Lifetime) : AbstractProgressIndicatorExBase() {

  val lifetime = Lifetime.create(life).lifetime
  val modelSignal = VariableSignal(Lifetime.create(lifetime), "model", MapModel())

  private volatile var stateChanged = false

  override fun onProgressChange() = fireUpdate()
  override fun onRunningChange() = fireUpdate()

  override fun initStateFrom(indicator: ProgressIndicator) {
    super.initStateFrom(indicator)
    fireUpdate()
  }

  override fun pushState() {
    stop()
    fireUpdate()
    stateChanged = true
    super.pushState()
  }

  override fun popState() {
    stop()
    fireUpdate()
    stateChanged = true
    super.popState()
  }

  private fun fireUpdate() {
    if (!stateChanged) {
      // need to capture actual state
      val model = toModel()
      UIUtil.invokeLaterIfNeeded {
        modelSignal.value = model
      }
    }
  }

  override fun stop() {
    if (stateChanged) return
    super.stop()
  }

  // todo separate logic
  private fun toModel(): MapModel {
    return MapModel(hashMapOf(
        "fraction" to PrimitiveModel(getFraction()),
        "text" to PrimitiveModel(getText()),
        "text2" to PrimitiveModel(getText2()),
        "canceled" to PrimitiveModel(isCanceled()),
        "running" to PrimitiveModel(isRunning())
    ))
  }
}
