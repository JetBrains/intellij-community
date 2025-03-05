// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * In progress of migration to new project structure provider, see KTIJ-31422
 *
 * Should be removed as a part of KTIJ-32817
 */
@get:ApiStatus.Internal
val useNewK2ProjectStructureProvider: Boolean
    get() = Registry.`is`("kotlin.use.new.project.structure.provider")