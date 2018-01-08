/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * An annotation which depicts that method returns an unmodifiable value or a variable
 * contains an unmodifiable value. Unmodifiable value means that calling methods which may
 * mutate this value (alter visible behavior) either don't have any effect or throw
 * an exception.
 * <p>
 * This annotation is experimental and may be changed/removed in future
 * without additional notice!
 * </p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@ApiStatus.Experimental
public @interface Unmodifiable {
}