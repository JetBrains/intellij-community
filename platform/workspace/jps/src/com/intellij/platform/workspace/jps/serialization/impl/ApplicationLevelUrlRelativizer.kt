// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.platform.workspace.storage.impl.url.UrlRelativizerImpl
import org.jetbrains.jps.model.serialization.PathMacroUtil

/**
 * The ApplicationLevelUrlRelativizer class is used to generate relative paths specific
 * to the application level, mainly used by [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelCacheImpl]
 * for cache serialization.
 *
 * This class extends the UrlRelativizer class and utilizes path macros obtained
 * from the [org.jetbrains.jps.model.serialization.PathMacroUtil.getGlobalSystemMacros()].
 */
open class ApplicationLevelUrlRelativizer(insideIdeProcess: Boolean) : UrlRelativizerImpl(
  PathMacroUtil.getGlobalSystemMacros(insideIdeProcess).entries.map {
    Pair(it.key, it.value)
  }
)