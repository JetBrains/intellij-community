// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.PathMapper
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.resolvedPromise
import java.awt.event.ActionListener

@ApiStatus.Experimental
class TargetPathFieldWithBrowseButton : TextFieldWithBrowseButton() {
  private var pathMapper: PathMapper? = null
  private var currentActionListener: ActionListener? = null

  fun getLocalPath(): String? = getTargetPathValue().maybeGetLocalValue()
  fun setLocalPath(path: String?) {
    val targetPath = path?.let { pathMapper.maybeConvertToRemote(it) }
    setText(targetPath)
  }

  fun addTargetActionListener(pathMapper: PathMapper?, listener: ActionListener?) {
    this.pathMapper = pathMapper
    if (currentActionListener != null) {
      removeActionListener(currentActionListener)
    }
    currentActionListener = listener
    super.addActionListener(listener)
  }

  private fun getTargetPathValue(): TargetValue<String>? {
    val targetPath = text.nullize(true) ?: return null
    val localValue = pathMapper.maybeConvertToLocal(targetPath)
    return TargetValue.create(localValue, resolvedPromise(targetPath))
  }
}