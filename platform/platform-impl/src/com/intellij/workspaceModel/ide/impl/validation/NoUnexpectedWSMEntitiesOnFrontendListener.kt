// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.validation

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity

// @Suppress("UsagesOfObsoleteApi") - async listener is fine here, but we need all the events, especially the very first one
internal class NoUnexpectedWSMEntitiesOnFrontendListener : WorkspaceModelChangeListener {
  private val unwantedEntities = listOf(
    ModuleEntity::class.java,
    LibraryEntity::class.java,
    SdkEntity::class.java,
  )

  init {
    if (!IdeProductMode.isFrontend) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun changed(event: VersionedStorageChange) {
    val violations = mutableListOf<Class<out WorkspaceEntity>>()

    for (unwantedClass in unwantedEntities) {
      val unwantedChanges = event.getChanges(unwantedClass)
      if (unwantedChanges.isNotEmpty()) {
        violations.add(unwantedClass)
      }
    }

    if (violations.isNotEmpty()) {
      thisLogger().error("It is not correct to change the following WSM entities on frontend: ${violations}")
    }
  }
}