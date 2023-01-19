// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.progress

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.wm.ex.ProgressIndicatorEx

internal open class DelegatingProgressIndicatorEx(indicator: ProgressIndicatorEx) :
  DelegatingProgressIndicator(indicator),
  ProgressIndicatorEx {

  private val delegateEx: ProgressIndicatorEx get() = delegate as ProgressIndicatorEx

  override fun addStateDelegate(delegate: ProgressIndicatorEx) = delegateEx.addStateDelegate(delegate)
  override fun finish(task: TaskInfo) = delegateEx.finish(task)
  override fun isFinished(task: TaskInfo): Boolean = delegateEx.isFinished(task)
  override fun wasStarted(): Boolean = delegateEx.wasStarted()
  override fun processFinish() = delegateEx.processFinish()
  override fun initStateFrom(indicator: ProgressIndicator) = delegateEx.initStateFrom(indicator)
}