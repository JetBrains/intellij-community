// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.module

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Set on a project by the plugin project wizard to disable aggressive error highlighting in a freshly generated plugin.xml.
 */
@ApiStatus.Internal
@JvmField
val DEVKIT_NEWLY_GENERATED_PROJECT: Key<Boolean> = Key.create("devkit.newly.generated.project")
