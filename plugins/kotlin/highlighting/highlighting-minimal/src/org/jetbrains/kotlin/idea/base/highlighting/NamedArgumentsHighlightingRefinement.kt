// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import org.jetbrains.annotations.ApiStatus

/**
 * Marker for a [BeforeResolveHighlightingExtension] that highlights named arguments itself,
 * together with a resolve-based refinement (context arguments).
 * When one is registered, the minimal `NamedArgumentsHighlightingExtension` stays silent.
 */
@ApiStatus.Internal
interface NamedArgumentsHighlightingRefinement
