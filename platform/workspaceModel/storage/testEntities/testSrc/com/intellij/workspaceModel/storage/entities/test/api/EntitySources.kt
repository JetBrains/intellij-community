package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.DummyParentEntitySource
import com.intellij.workspaceModel.storage.EntitySource

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