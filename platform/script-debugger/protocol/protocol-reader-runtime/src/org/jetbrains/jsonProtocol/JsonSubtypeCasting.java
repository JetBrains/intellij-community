// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.jsonProtocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks method as method casting to a subtype. Normally the method return type should be
 * some other json type, which serves as subtype; the subtype interface must extend
 * {@link JsonSubtype} (with correct generic parameter).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface JsonSubtypeCasting {
  boolean reinterpret() default false;
}
