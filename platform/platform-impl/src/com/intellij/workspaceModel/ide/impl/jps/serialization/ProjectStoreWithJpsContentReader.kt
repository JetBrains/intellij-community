// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader

interface ProjectStoreWithJpsContentReader : IProjectStore {
  fun createContentReader(): JpsFileContentReaderWithCache
}

interface JpsFileContentReaderWithCache : JpsFileContentReader {
  fun clearCache()
}