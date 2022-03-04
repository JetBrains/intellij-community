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