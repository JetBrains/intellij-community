// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;

/**
 * Marker annotation for strings that don't require localization but still could be displayed in UI.
 * Examples:
 * <ul>
 *   <li>File name or path</li>
 *   <li>Project name</li>
 *   <li>URL</li>
 *   <li>Programming language or framework name</li>
 * </ul>
 */
@ApiStatus.Experimental
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER})
public @interface NlsSafe {
}
