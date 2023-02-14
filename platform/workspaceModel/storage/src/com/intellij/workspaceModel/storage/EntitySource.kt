// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.url.VirtualFileUrl

/**
 * Declares a place from which an entity came.
 * Usually contains enough information to identify a project location.
 * An entity source must be serializable along with entities, so there are some limits to implementation.
 * It must be a data class which contains read-only properties of the following types:
 * * primitive types;
 * * String;
 * * enum;
 * * [List] of another allowed type;
 * * another data class with properties of the allowed types;
 * * sealed abstract class where all implementations satisfy these requirements.
 */
interface EntitySource {
  val virtualFileUrl: VirtualFileUrl?
    get() = null
}

/**
 * Marker interface to represent entities which properties aren't loaded and which were added to the storage because other entities requires
 * them. Entities which sources implements this interface don't replace existing entities when [MutableEntityStorage.replaceBySource]
 * is called.
 *
 * For example if we have `FacetEntity` which requires `ModuleEntity`, and need to load facet configuration from *.iml file and load the module
 * configuration from some other source, we may use this interface to mark `entitySource` for `ModuleEntity`. This way when content of *.iml
 * file is applied to the model via [MutableEntityStorage.replaceBySource], it won't overwrite actual configuration
 * of `ModuleEntity`.
 */
interface DummyParentEntitySource : EntitySource
