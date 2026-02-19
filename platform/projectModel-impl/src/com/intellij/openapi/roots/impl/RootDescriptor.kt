// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface RootDescriptor {
  val root: VirtualFile
}

@Internal
data class ModuleRootDescriptor(
  override val root: VirtualFile,
  val module: Module,
) : RootDescriptor

@Internal
data class LibraryRootDescriptor(
  override val root: VirtualFile,
  val library: Library,
) : RootDescriptor

@Internal
data class SdkRootDescriptor(
  override val root: VirtualFile,
  val sdk: Sdk,
) : RootDescriptor

@Internal
data class DummyRootDescriptor(
  override val root: VirtualFile,
) : RootDescriptor