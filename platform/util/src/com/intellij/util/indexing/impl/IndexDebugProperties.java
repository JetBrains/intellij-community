/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.impl;

import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.IndexId;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class IndexDebugProperties {
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
