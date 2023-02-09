// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

// Convenience-shortcut
interface WorkspaceFilteringDsl
    : ModulesFilteringDsl, OrderEntriesFilteringSupport, FacetSettingsFilteringDsl, ContentRootsFilteringDsl
