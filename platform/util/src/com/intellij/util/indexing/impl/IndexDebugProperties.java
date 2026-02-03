// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.ApiStatus;

import static com.intellij.util.SystemProperties.getBooleanProperty;

@ApiStatus.Internal
public final class IndexDebugProperties {
  public static final ThreadLocal<IndexId<?, ?>> DEBUG_INDEX_ID = new ThreadLocal<>();

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean DEBUG = getBooleanProperty("intellij.idea.indices.debug", false);

  public static volatile boolean IS_UNIT_TEST_MODE = false;

  public static volatile boolean IS_IN_STRESS_TESTS = false;

  public static final boolean EXTRA_SANITY_CHECKS = getBooleanProperty(
    "intellij.idea.indices.debug.extra.sanity",
    false //DEBUG // todo https://youtrack.jetbrains.com/issue/IDEA-134916
  );
}
