// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.ApiStatus;

/**
 * Provides a visibility hint that can be used by client-side stack frames hiding.
 * <p>
 * The value must not change during the frame lifetime.
 */
@ApiStatus.Internal
public interface XStackFrameVisibilityInfoProvider {
  boolean shouldHide();
}
