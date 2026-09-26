// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.project.Project

internal class TodoViewChangesSupportFactoryImpl : TodoViewChangesSupportFactory {

  override fun isAvailable(): Boolean = !shouldUseSplitTodo()

  override fun create(project: Project): TodoViewChangesSupport = TodoViewChangesSupportImpl()
}