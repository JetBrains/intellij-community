// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface meaning that the implementations contain weak/soft references that sometimes need some housekeeping, e.g. removing references to the gc-ed objects
 * {@link #processQueue} should remove these gc-ed/stale references
 */
@ApiStatus.Internal
public interface ReferenceQueueable {
  /**
   * Process internal reference queues and remove stale references from the datastructures inside.
   * E.g., in the case of SoftValueHashMap, this method will remove key/value associations from that map for which the value was garbage-collected.
   * @return true if some references were collected, false otherwise
   */
  @ApiStatus.Internal
  boolean processQueue();
}
