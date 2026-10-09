// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link OrderEntry} that an {@link OrderRootsEnumerator} walk reached, together with the filter it would apply to
 * that entry's roots - everything {@link OrderRootsEnumerator#getRootEntries()} knows about an entry before it starts
 * resolving roots.
 * <p>
 * It exists so that a caller which only needs "which entries are reachable, and in which order" does not have to
 * materialise the roots behind them. The roots of one entry are a function of the entry alone, so a caller that keeps
 * them globally - one list per module, library or SDK - can reconstruct the walk's result from a list of these, at a
 * fraction of the memory a root-per-entry list costs.
 *
 * @param includeProduction whether production source roots of {@code orderEntry} take part; always {@code true} for a
 *                          library or an SDK entry, and {@code false} only for a production-on-test module dependency
 * @param includeTests      whether test source roots take part; always {@code true} for a library or an SDK entry,
 *                          since their roots are not split that way
 * @see OrderRootsEnumerator#getEnumeratedEntries()
 */
@ApiStatus.Experimental
public record EnumeratedEntry(
  @NotNull OrderEntry orderEntry,
  @NotNull OrderRootType rootType,
  boolean includeProduction,
  boolean includeTests
) {}
