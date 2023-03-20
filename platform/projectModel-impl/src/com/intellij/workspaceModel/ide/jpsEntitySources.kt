// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspaceModel.jps.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.EntitySource

/**
 * Represents entities imported from external project system.
 */
data class ExternalEntitySource(val displayName: String, val id: String) : EntitySource

fun JpsImportedEntitySource.toExternalSource(): ProjectModelExternalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(externalSystemId)

/**
 * Represents entities which are added to the model automatically and shouldn't be persisted
 */
object NonPersistentEntitySource : EntitySource
