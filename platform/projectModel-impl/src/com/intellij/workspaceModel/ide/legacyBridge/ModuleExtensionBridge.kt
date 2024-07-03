// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface for implementations of [com.intellij.openapi.roots.ModuleExtension] which are created via [ModuleExtensionBridgeFactory].
 */
@ApiStatus.Internal
interface ModuleExtensionBridge {
}