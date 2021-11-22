// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

data class SampleEntitySource(val name: String) : EntitySource
object MySource : EntitySource {
  override fun toString(): String = "MySource"
}

object AnotherSource : EntitySource {
  override fun toString(): String = "AnotherSource"
}

object MyDummyParentSource : DummyParentEntitySource {
  override fun toString(): String = "DummyParent"
}