// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.propertyBased

import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.createEmptyBuilder
import com.intellij.workspaceModel.storage.entities.test.api.AnotherSource
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.SampleEntitySource
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.MutableEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.toClassId
import org.jetbrains.jetCheck.GenerationEnvironment
import org.jetbrains.jetCheck.Generator

internal val newEmptyWorkspace: Generator<MutableEntityStorageImpl>
  get() = Generator.constant(createEmptyBuilder())

internal class EntityIdGenerator(private val storage: MutableEntityStorageImpl) : java.util.function.Function<GenerationEnvironment, EntityId?> {
  override fun apply(t: GenerationEnvironment): EntityId? {
    val filtered = storage.entitiesByType.entityFamilies.asSequence().filterNotNull().flatMap { it.entities.filterNotNull().asSequence() }
    if (filtered.none()) return null
    val nonNullIds = filtered.toList()
    val id = t.generate(Generator.integers(0, nonNullIds.lastIndex))
    return nonNullIds[id].createEntityId()
  }

  companion object {
    fun create(storage: MutableEntityStorageImpl) = Generator.from(EntityIdGenerator(storage))
  }
}

internal class EntityIdOfFamilyGenerator(private val storage: MutableEntityStorageImpl,
                                         private val family: Int) : java.util.function.Function<GenerationEnvironment, EntityId?> {
  override fun apply(t: GenerationEnvironment): EntityId? {
    val entityFamily = storage.entitiesByType.entityFamilies.getOrNull(family) ?: return null
    val existingEntities = entityFamily.entities.filterNotNull()
    if (existingEntities.isEmpty()) return null
    val randomId = t.generate(Generator.integers(0, existingEntities.lastIndex))
    return existingEntities[randomId].createEntityId()
  }

  companion object {
    fun create(storage: MutableEntityStorageImpl, family: Int) = Generator.from(EntityIdOfFamilyGenerator(storage, family))
  }
}

internal val sources = Generator.anyOf(
  Generator.constant(MySource),
  Generator.constant(AnotherSource),
  Generator.from {
    SampleEntitySource(it.generate(randomNames))
  }
)
internal val randomNames = Generator.sampledFrom(
  "education", "health", "singer", "diamond", "energy", "imagination", "suggestion", "vehicle", "marriage", "people", "revolution",
  "decision", "homework", "heart", "candidate", "appearance", "poem", "outcome", "shopping", "government", "dinner", "unit", "quantity",
  "construction", "assumption", "development", "understanding", "committee", "complaint", "bedroom", "collection", "administration",
  "college", "addition", "height", "university", "map", "requirement", "people", "expression", "statement", "alcohol", "resolution",
  "charity", "opinion", "king", "wedding", "heart", "basis", "chemistry", "opportunity", "selection", "passenger", "teacher", "driver",
  "attitude", "presentation", "philosophy", "poetry", "significance", "editor", "examination", "buyer", "baseball", "disaster",
  "reflection", "dad", "activity", "instance", "idea"
)

internal inline fun <reified T : WorkspaceEntity> parentGenerator(storage: MutableEntityStorageImpl): Generator<T?> {
  return Generator.from {
    val classId = T::class.java.toClassId()
    val parentId = it.generate(
      EntityIdOfFamilyGenerator.create(storage, classId)) ?: return@from null
    storage.entityDataByIdOrDie(parentId).createEntity(storage) as T
  }
}
