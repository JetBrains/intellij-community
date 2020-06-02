// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.util.registry.Registry

object StrictMode {
  var enabled = Registry.`is`("ide.new.project.model.strict.mode", true)
}