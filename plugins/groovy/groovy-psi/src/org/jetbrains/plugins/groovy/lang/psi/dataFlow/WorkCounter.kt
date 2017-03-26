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


package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import com.intellij.codeInspection.dataFlow.WorkingTimeMeasurer
import com.intellij.openapi.util.registry.Registry

internal class WorkCounter {

  private val myMeasurer by lazy(LazyThreadSafetyMode.NONE) {
    val msLimit = Registry.intValue("ide.dfa.time.limit.online").toLong()
    WorkingTimeMeasurer(msLimit * 1000 * 1000)
  }
  private var myCount: Int = 0

  /**
   * Checks [WorkingTimeMeasurer.isTimeOver] every 512 invocations
   * @return `true` if time is over
   */
  fun isTimeOver(): Boolean = ++myCount % 512 == 0 && myMeasurer.isTimeOver
}