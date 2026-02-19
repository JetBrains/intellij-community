package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase

// Present in metadata

internal object MetadataStorageImpl: MetadataStorageBase() {
  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentObjectSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentClassSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentInterfaceSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentInheritedPresentSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.OuterClass1\$PresentInnerEntitySource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentInheritedAbsentSource", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentRecursive1", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentRecursive2", metadataHash = 0)
    addMetadataHash(typeFqn = "com.intellij.workspaceModel.test.api.PresentRecursiveParentAbsent", metadataHash = 0)
  }
}

object PresentObjectSource : EntitySource

class PresentClassSource : EntitySource

interface PresentInterfaceSource : EntitySource

class PresentInheritedPresentSource : PresentInterfaceSource

class OuterClass1 {
  class PresentInnerEntitySource : EntitySource
}

// Absent in metadata

object <warning descr="Absent EntitySource metadata">AbsentObjectSource</warning> : EntitySource

class <warning descr="Absent EntitySource metadata">AbsentClassSource</warning> : EntitySource

interface <warning descr="Absent EntitySource metadata">AbsentInterfaceSource</warning> : EntitySource

class <warning descr="Absent EntitySource metadata">AbsentInheritedAbsentSource</warning> : AbsentInterfaceSource

class <warning descr="Absent parent EntitySource metadata: AbsentInterfaceSource">PresentInheritedAbsentSource</warning> : AbsentInterfaceSource

class OuterClass2 {
  class <warning descr="Absent EntitySource metadata">AbsentInnerEntitySource</warning> : EntitySource
}