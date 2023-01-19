// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

open class NotGeneratedRuntimeException(message: String) : RuntimeException(message)
class NotGeneratedMethodRuntimeException(val methodName: String)
  : NotGeneratedRuntimeException("Method `$methodName` uses default implementation. Please regenerate entities")