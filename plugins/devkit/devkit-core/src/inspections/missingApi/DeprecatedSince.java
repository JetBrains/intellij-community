// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;

/**
 * Do not use!
 *
 * This annotation is used to show external annotations for the IntelliJ APIs' history [MP-3340].
 * Specifically, it specifies in which IDE build the corresponding API element was marked @Deprecated.
 * It may be useful to know for how long an API element is deprecated, as part of IntelliJ APIs cleanup [IDEA-196843].
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
  ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE
})
public @interface DeprecatedSince {
  /**
   * Specifies in which version the API was marked deprecated.
   */
  String sinceVersion() default "";
}