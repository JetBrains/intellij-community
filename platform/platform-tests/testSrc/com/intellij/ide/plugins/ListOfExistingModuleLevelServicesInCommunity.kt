// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

/**
 * Module-level services are deprecated in intellij monorepo sources (IJPL-179169), don't add new ones.
 * Use application-level or project-level services instead and pass the 'Module' instance as a parameter if needed.
 * If you need to store user-defined configuration in an *.iml file, use [com.intellij.openapi.components.CustomImlComponentService].
 */
val existingModuleLevelServicesInCommunity: Set<String> = setOf(
  "com.android.tools.idea.databinding.DataBindingAnnotationsService",
  "com.android.tools.idea.databinding.module.LayoutBindingModuleCache",
  "com.android.tools.idea.lang.androidSql.room.RoomSchemaManager",
  "com.android.tools.idea.mlkit.MlModuleService",
  "com.android.tools.idea.model.MergedManifestManager",
  "com.android.tools.idea.model.MergedManifestModificationTracker",
  "com.android.tools.idea.module.ModuleDisposableService",
  "com.android.tools.idea.nav.safeargs.module.ModuleNavigationResourcesModificationTracker",
  "com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService",
  "com.android.tools.idea.nav.safeargs.module.SafeArgsModeModuleService",
  "com.android.tools.idea.res.AndroidDependenciesCache",
  "com.android.tools.idea.res.ResourceFolderRepositoryBackgroundActions",
  "com.android.tools.idea.res.StudioResourceIdManager",
  "com.android.tools.idea.uibuilder.palette.NlPaletteModel",
  "org.jetbrains.android.TagToClassMapperImpl",
  "org.jetbrains.android.facet.ResourceFolderManager",
  "org.jetbrains.android.resourceManagers.ModuleResourceManagers",
  "org.jetbrains.android.uipreview.ModuleClassLoaderOverlays",
  "org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl",
)