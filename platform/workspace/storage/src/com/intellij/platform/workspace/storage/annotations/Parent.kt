// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.annotations

/**
 * Marks a property in an interface extending [WorkspaceEntity][com.intellij.platform.workspace.storage.WorkspaceEntity] as referring to
 * a parent entity.
 * The property may be declared either inside the interface or as an extension property. It may be nullable.
 * 
 * `@Parent val parent: ParentEntity`
 *
 * `ParentEntity` must be an entity interface extending [WorkspaceEntity][com.intellij.platform.workspace.storage.WorkspaceEntity],
 * and must declare a property which type is equal to the type of the child entity with nullable mark (`?`) or a list of such entities.
 * 
 * No special annotation is needed for the property pointing to the child entity. 
 * 
 * Non-null reference to a single child entity is not supported. Such references would mean that the child and the parent entities are
 * always added and removed together. So it's simpler to just put all properties from the child entity to the parent entity instead. If you
 * want to group several related properties, you may extract them to a separate data class instead of defining such a child entity.
 * 
 * More details are provided in [WorkspaceEntity][com.intellij.platform.workspace.storage.WorkspaceEntity] documentation.
 */
@Target(AnnotationTarget.PROPERTY)
public annotation class Parent