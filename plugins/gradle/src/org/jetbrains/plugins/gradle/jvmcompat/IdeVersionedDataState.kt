// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.components.BaseState

abstract class IdeVersionedDataState : BaseState() {
  var ideVersion by string()
  var isDefault by property(true)
  var lastUpdateTime: Long by property(0L)
}