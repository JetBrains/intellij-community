// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.concurrency.Promise
import java.io.File

@Experimental
interface ParameterTargetValue {
  val localValue: String
}

@Experimental
sealed class ParameterTargetValuePart(override val localValue: String) : ParameterTargetValue {
  open val pathToUpload: String? = null

  class Const(localValue: String) : ParameterTargetValuePart(localValue) {
    override fun toString(): String = localValue
  }

  class Path(localPath: String) : ParameterTargetValuePart(localPath) {
    constructor(file: File) : this(file.absolutePath)

    override val pathToUpload: String
      get() = localValue

    override fun toString(): String = "ParameterTargetValuePart.Path $localValue"
  }

  object PathSeparator : ParameterTargetValuePart(File.pathSeparator)

  class PromiseValue(localValue: String, val targetValue: Promise<String>) : ParameterTargetValuePart(localValue)
}