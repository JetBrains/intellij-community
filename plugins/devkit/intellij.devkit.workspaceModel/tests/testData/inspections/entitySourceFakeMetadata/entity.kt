package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource

// Fake MetadataStorageBase
abstract class MetadataStorageBase() {
  @Suppress("UNUSED_PARAMETER")
  protected fun addMetadataHash(typeFqn: String, metadataHash: Int) {
  }

  protected abstract fun initializeMetadataHash()
}

internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentObjectSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentClassSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentInterfaceSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentInheritedPresentSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.OuterClass1\$PresentInnerEntitySource", metadataHash = 0)
  }
}

object <warning descr="Absent EntitySource metadata">PresentObjectSource</warning> : EntitySource

class <warning descr="Absent EntitySource metadata">PresentClassSource</warning> : EntitySource

interface <warning descr="Absent EntitySource metadata">PresentInterfaceSource</warning> : EntitySource

class <warning descr="Absent EntitySource metadata">PresentInheritedPresentSource</warning> : PresentInterfaceSource

class OuterClass1 {
  class <warning descr="Absent EntitySource metadata">PresentInnerEntitySource</warning> : EntitySource
}
