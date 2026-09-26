// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.DummyParentEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

data class SampleEntitySource(val name: String) : EntitySource
data object MySource : EntitySource

data object AnotherSource : EntitySource

data object MyDummyParentSource : DummyParentEntitySource

class VFUEntitySource(private val vfu: VirtualFileUrl) : EntitySource {
  override val virtualFileUrl: VirtualFileUrl
    get() = vfu
}
