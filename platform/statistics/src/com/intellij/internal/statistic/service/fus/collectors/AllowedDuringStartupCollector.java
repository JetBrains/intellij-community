// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface that allows earlier execution of application usage collector.
 * <br/>
 * <b>Do not</b> use without approval from Product Analytics Platform Team.
 */
@ApiStatus.Internal
public interface AllowedDuringStartupCollector {
}
