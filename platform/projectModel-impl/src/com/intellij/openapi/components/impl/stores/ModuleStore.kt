// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

interface ModuleStore : IComponentStore {
  fun setPath(path: Path, virtualFile: VirtualFile?, isNew: Boolean)
}