// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated To be removed in 2019.1. Please use {@link AccessToNonThreadSafeStaticFieldFromInstanceInspection} directly
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.1")
public class AccessToNonThreadSafeStaticFieldFromInstanceInspectionBase extends AccessToNonThreadSafeStaticFieldFromInstanceInspection {
}
