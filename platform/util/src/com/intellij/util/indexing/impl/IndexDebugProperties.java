// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class IndexDebugProperties {
  public static final ThreadLocal<IndexId<?, ?>> DEBUG_INDEX_ID = new ThreadLocal<>();

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean DEBUG = SystemProperties.getBooleanProperty(
    "intellij.idea.indices.debug",
    false
  );

  public static final boolean EXTRA_SANITY_CHECKS = SystemProperties.getBooleanProperty(
    "intellij.idea.indices.debug.extra.sanity",
    false //DEBUG // todo https://youtrack.jetbrains.com/issue/IDEA-134916
  );
}
