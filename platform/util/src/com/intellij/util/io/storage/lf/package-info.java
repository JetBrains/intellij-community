// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Re-implementation of {@link com.intellij.util.io.storage} on top of {@link com.intellij.util.io.FilePageCacheLockFree}
 */
@Internal
package com.intellij.util.io.storage.lf;

import org.jetbrains.annotations.ApiStatus.Internal;
