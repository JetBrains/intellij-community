// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

object LibraryNameGenerator {
  private const val UNIQUE_INDEX_LIBRARY_NAME_SUFFIX = "-d1a6f608-UNIQUE-INDEX-f29c-4df6-"
  const val UNNAMED_LIBRARY_NAME_PREFIX = "#"

  fun getLegacyLibraryName(libraryId: LibraryId): String? {
    if (libraryId.name.startsWith(UNNAMED_LIBRARY_NAME_PREFIX)) return null
    if (libraryId.name.contains(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)) return libraryId.name.substringBefore(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)
    return libraryId.name
  }

  fun generateLibraryEntityName(legacyLibraryName: String?, exists: (String) -> Boolean): String {
    if (legacyLibraryName == null) {
      var index = 1
      while (true) {
        val candidate = "$UNNAMED_LIBRARY_NAME_PREFIX$index"
        if (!exists(candidate)) {
          return candidate
        }

        index++
      }
      @Suppress("UNREACHABLE_CODE")
      error("Unable to suggest unique name for unnamed module library")
    }

    return generateUniqueLibraryName(legacyLibraryName, exists)
  }

  fun generateUniqueLibraryName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name

    var index = 1
    while (true) {
      val candidate = "$name$UNIQUE_INDEX_LIBRARY_NAME_SUFFIX$index"
      if (!exists(candidate)) {
        return candidate
      }

      index++
    }
  }

  fun getLibraryTableId(level: String): LibraryTableId = when (level) {
    JpsLibraryTableSerializer.MODULE_LEVEL -> error("this method isn't supposed to be used for module-level libraries")
    JpsLibraryTableSerializer.PROJECT_LEVEL -> LibraryTableId.ProjectLibraryTableId
    else -> LibraryTableId.GlobalLibraryTableId(level)
  }
}

