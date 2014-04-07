// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.protocolReader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as interface to json type object. This way user may hold JSON object in
 * form of statically typed Java interface. The interface provides methods for reading properties
 * (here called fields, because we imply there are "types" in JSON) and for accessing subtypes.
 * <p/>
 * In this design casting to subtypes means getting a different object of the subtype interface.
 * For a type interface, a set of subtypes is defined by its methods
 * with {@link JsonSubtypeCasting} annotation. These methods provide access to subtype objects.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonType {
  boolean allowsOtherProperties() default false;
}
