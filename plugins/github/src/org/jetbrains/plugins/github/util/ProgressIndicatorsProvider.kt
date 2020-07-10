// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import java.util.concurrent.ConcurrentHashMap

class ProgressIndicatorsProvider : Disposable {
  private val indicators = ConcurrentHashMap.newKeySet<ProgressIndicator>()

  fun acquireIndicator(): ProgressIndicator {
    val indicator = EmptyProgressIndicator()
    indicators.add(indicator)
    return indicator
  }

  fun releaseIndicator(indicator: ProgressIndicator) = indicators.remove(indicator)

  override fun dispose() = indicators.forEach(ProgressIndicator::cancel)
}