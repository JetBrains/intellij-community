// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * This package contains an implementation of a generic storage for entities describing the user's workspace for IntelliJ Platform.
 * <p>
 * Use {@link com.intellij.platform.backend.workspace.WorkspaceModel WorkspaceModel} interface to access an instance of this storage used by
 * the IDE process.  
 * <p>
 * Types of entities in the storage are represented by interfaces extending {@link com.intellij.platform.workspace.storage.WorkspaceEntity}.
 * Their instances are stored in {@link com.intellij.platform.workspace.storage.VersionedEntityStorage}, which provides access to the 
 * current {@link com.intellij.platform.workspace.storage.ImmutableEntityStorage} and allows to modify entities via
 * {@link com.intellij.platform.workspace.storage.MutableEntityStorage}. Modifications are performed on copies of the original entities,
 * so they don't affect old snapshots. 
 * <p>
 * Entities are organized into a direct acyclic graph by optional parent-child relationships. The storage maintains the consistency of such 
 * relations, i.e., if a child entity has a non-null reference to a parent entity, the child entity is removed when the parent entity
 * is removed or changed to point to a different child entity. Also, symbolic (soft) references between entities
 * are supported via {@link com.intellij.platform.workspace.storage.SymbolicEntityId}. Properties of entities may be of a restricted set of
 * types only, see {@link com.intellij.platform.workspace.storage.WorkspaceEntity} for details. It's possible to link objects of arbitrary
 * types with entities using {@link com.intellij.platform.workspace.storage.ExternalEntityMapping}.
 * <p>
 * All classes in this package <strong>are experimental</strong> and their API may change in future versions.
 */
@ApiStatus.Experimental
package com.intellij.platform.workspace.storage;

import org.jetbrains.annotations.ApiStatus;