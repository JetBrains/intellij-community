package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract

@GeneratedCodeApiVersion(3)
interface ModifiableChildEntity : ModifiableWorkspaceEntity<ChildEntity>, ModifiableBaseEntity<ChildEntity> {
  override var entitySource: EntitySource
  override var name: String
  override var moduleId: SimpleId
  override var aBaseEntityProperty: String
  override var dBaseEntityProperty: String
  override var bBaseEntityProperty: String
  override var sealedDataClassProperty: BaseDataClass
  var cChildEntityProperty: String
}

internal object ChildEntityType : EntityType<ChildEntity, ModifiableChildEntity>() {
  override val entityClass: Class<ChildEntity> get() = ChildEntity::class.java
  operator fun invoke(
    name: String,
    moduleId: SimpleId,
    aBaseEntityProperty: String,
    dBaseEntityProperty: String,
    bBaseEntityProperty: String,
    sealedDataClassProperty: BaseDataClass,
    cChildEntityProperty: String,
    entitySource: EntitySource,
    init: (ModifiableChildEntity.() -> Unit)? = null,
  ): ModifiableChildEntity {
    val builder = builder()
    builder.name = name
    builder.moduleId = moduleId
    builder.aBaseEntityProperty = aBaseEntityProperty
    builder.dBaseEntityProperty = dBaseEntityProperty
    builder.bBaseEntityProperty = bBaseEntityProperty
    builder.sealedDataClassProperty = sealedDataClassProperty
    builder.cChildEntityProperty = cChildEntityProperty
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyChildEntity(
  entity: ChildEntity,
  modification: ModifiableChildEntity.() -> Unit,
): ChildEntity = modifyEntity(ModifiableChildEntity::class.java, entity, modification)

@JvmOverloads
@JvmName("createChildEntity")
fun ChildEntity(
  name: String,
  moduleId: SimpleId,
  aBaseEntityProperty: String,
  dBaseEntityProperty: String,
  bBaseEntityProperty: String,
  sealedDataClassProperty: BaseDataClass,
  cChildEntityProperty: String,
  entitySource: EntitySource,
  init: (ModifiableChildEntity.() -> Unit)? = null,
): ModifiableChildEntity =
  ChildEntityType(name, moduleId, aBaseEntityProperty, dBaseEntityProperty, bBaseEntityProperty, sealedDataClassProperty,
                  cChildEntityProperty, entitySource, init)
