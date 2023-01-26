// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Obsolete

// we don't need such a symbol in a global scope
@get:Obsolete
val application: Application
  get() = ApplicationManager.getApplication()
