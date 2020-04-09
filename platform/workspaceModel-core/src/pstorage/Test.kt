// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.pstorage.references.ManyToOne
import com.intellij.workspace.api.pstorage.references.MutableManyToOne
import com.intellij.workspace.api.pstorage.references.MutableOneToMany
import com.intellij.workspace.api.pstorage.references.OneToMany


fun main() {
  val pStoreBuilder = PEntityStorageBuilder.create()
  val createdEntity = pStoreBuilder.addEntity(ModifiablePFolderEntity::class.java, MySource) {
    this.id
    this.data = "xxxx"
  }
  pStoreBuilder.addEntity(ModifiablePSubFolderEntity::class.java, MySource) {
    this.data = "XYZ"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(ModifiablePSubFolderEntity::class.java, MySource) {
    this.data = "XYZ2"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(ModifiablePSoftSubFolderEntity::class.java, MySource) {
    this.parent = createdEntity
  }

  printStorage(pStoreBuilder)
  println("---------------")
  pStoreBuilder.removeEntity(pStoreBuilder.entities(PFolderEntity::class.java).first())
  printStorage(pStoreBuilder)
}

private fun printStorage(pStoreBuilder: TypedEntityStorageBuilder) {
  println(pStoreBuilder.entities(PFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSubFolderEntity::class.java).toList())
  println(pStoreBuilder.entities(PSoftSubFolderEntity::class.java).toList())

  println(pStoreBuilder.entities(PSubFolderEntity::class.java).firstOrNull()?.parent)
  println(pStoreBuilder.entities(PFolderEntity::class.java).firstOrNull()?.children?.toList())
  println(pStoreBuilder.entities(PFolderEntity::class.java).firstOrNull()?.softChildren?.toList())
}

internal class PFolderEntityData : PEntityData<PFolderEntity>() {
  lateinit var data: String
}

internal class PSoftSubFolderEntityData : PEntityData<PSoftSubFolderEntity>()

internal class PSubFolderEntityData : PEntityData<PSubFolderEntity>() {
  lateinit var data: String
}

internal class PFolderEntity(
  val data: String
) : PTypedEntity() {
  val children: Sequence<PSubFolderEntity> by OneToMany.HardRef(PSubFolderEntity::class)
  val softChildren: Sequence<PSoftSubFolderEntity> by OneToMany.SoftRef(PSoftSubFolderEntity::class)
}

internal class PSoftSubFolderEntity : PTypedEntity() {
  val parent: PFolderEntity? by ManyToOne.SoftRef(PFolderEntity::class)
}

internal class PSubFolderEntity(
  val data: String
) : PTypedEntity() {
  val parent: PFolderEntity? by ManyToOne.HardRef.Nullable(PFolderEntity::class)
}

internal class ModifiablePFolderEntity : PModifiableTypedEntity<PFolderEntity>() {
  var data: String by EntityDataDelegation()

  var children: Sequence<PSubFolderEntity> by MutableOneToMany.HardRef(PFolderEntity::class, PSubFolderEntity::class)
  var softChildren: Sequence<PSoftSubFolderEntity> by MutableOneToMany.SoftRef(PFolderEntity::class, PSoftSubFolderEntity::class)
}

internal class ModifiablePSubFolderEntity : PModifiableTypedEntity<PSubFolderEntity>() {
  var data: String by EntityDataDelegation()

  var parent: PFolderEntity by MutableManyToOne.HardRef.NotNull(PSubFolderEntity::class, PFolderEntity::class)
}

internal class ModifiablePSoftSubFolderEntity : PModifiableTypedEntity<PSoftSubFolderEntity>() {
  var parent: PFolderEntity? by MutableManyToOne.SoftRef(PSoftSubFolderEntity::class, PFolderEntity::class)
}

internal object MySource : EntitySource