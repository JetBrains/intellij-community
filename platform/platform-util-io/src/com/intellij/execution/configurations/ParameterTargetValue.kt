// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.netty.bootstrap.com.intellij.execution.configurations

import org.jetbrains.concurrency.Promise
import java.io.File

interface ParameterTargetValue {
  val localValue: String
}

sealed class ParameterTargetValuePart(override val localValue: String) : ParameterTargetValue {
  open val pathToUpload: String? = null

  class Const(localValue: String) : ParameterTargetValuePart(localValue) {
    override fun toString() = localValue
  }

  class Path(localPath: String) : ParameterTargetValuePart(localPath) {
    constructor(file: File) : this(file.absolutePath)

    override val pathToUpload
      get() = localValue

    override fun toString() = "ParameterTargetValuePart.Path $localValue"
  }

  class PromiseValue(localValue: String, val targetValue: Promise<String>) : ParameterTargetValuePart(localValue)
}