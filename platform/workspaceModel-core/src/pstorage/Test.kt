// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.TypedEntityStorageBuilder


fun main() {
  val pStoreBuilder = PEntityStorageBuilder.create()
  val createdEntity = pStoreBuilder.addEntity(PFolderModifiableEntity::class.java, MySource) {
    this.id
    this.data = "xxxx"
  }
  pStoreBuilder.addEntity(PSubFolderModifiableEntity::class.java, MySource) {
    this.data = "XYZ"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(PSubFolderModifiableEntity::class.java, MySource) {
    this.data = "XYZ2"
    this.parent = createdEntity
  }
  pStoreBuilder.addEntity(PSoftSubFolderModifiableEntity::class.java, MySource) {
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
) : PTypedEntity<PFolderEntity>() {
  val children: Sequence<PSubFolderEntity> by OneToMany.HardRef(PSubFolderEntity::class)
  val softChildren: Sequence<PSoftSubFolderEntity> by OneToMany.SoftRef(PSoftSubFolderEntity::class)
}

internal class PSoftSubFolderEntity : PTypedEntity<PSoftSubFolderEntity>() {
  val parent: PFolderEntity? by ManyToOne.SoftRef(PFolderEntity::class)
}

internal class PSubFolderEntity(
  val data: String
) : PTypedEntity<PSubFolderEntity>() {
  val parent: PFolderEntity? by ManyToOne.HardRef(PFolderEntity::class)
}

internal class PFolderModifiableEntity : PModifiableTypedEntity<PFolderEntity>() {
  var data: String by EntityData()

  var children: Sequence<PSubFolderEntity> by MutableOneToMany.HardRef(PFolderEntity::class, PSubFolderEntity::class)
  var softChildren: Sequence<PSoftSubFolderEntity> by MutableOneToMany.SoftRef(PFolderEntity::class, PSoftSubFolderEntity::class)
}

internal class PSubFolderModifiableEntity : PModifiableTypedEntity<PSubFolderEntity>() {
  var data: String by EntityData()

  var parent: PFolderEntity by MutableManyToOne.HardRef(PSubFolderEntity::class, PFolderEntity::class)
}

internal class PSoftSubFolderModifiableEntity : PModifiableTypedEntity<PSoftSubFolderEntity>() {
  var parent: PFolderEntity? by MutableManyToOne.SoftRef(PSoftSubFolderEntity::class, PFolderEntity::class)
}

internal object MySource : EntitySource