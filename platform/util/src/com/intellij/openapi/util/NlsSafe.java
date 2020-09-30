// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.*;

/**
 * Marker annotation for strings that don't require localization but still could be displayed in UI.
 * <p>
 * Examples:
 * <ul>
 *   <li>File name or path</li>
 *   <li>Project name</li>
 *   <li>URL</li>
 *   <li>Programming language or framework name</li>
 * </ul>
 * Avoid using NlsSafe just to suppress the "hardcoded string" inspection warning. Use @{@link NonNls} 
 * if something is not intended to be displayed to the user: internal identifier, XML tag attribute, 
 * substring to be searched in the external process output, etc.
 */
@ApiStatus.Experimental
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface NlsSafe {
}
