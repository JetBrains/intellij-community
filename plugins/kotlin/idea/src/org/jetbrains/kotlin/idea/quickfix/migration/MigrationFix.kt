// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.migration

import org.jetbrains.kotlin.idea.configuration.MigrationInfo

/**
 * Marker interface for inspections that can be used during kotlin migrations
 */
interface MigrationFix {
    fun isApplicable(migrationInfo: MigrationInfo): Boolean
}
