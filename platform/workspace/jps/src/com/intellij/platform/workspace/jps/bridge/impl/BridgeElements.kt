// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.serialization.JpsPathMapper

internal val JpsModel?.pathMapper: JpsPathMapper
  get() = this?.global?.pathMapper ?: JpsPathMapper.IDENTITY