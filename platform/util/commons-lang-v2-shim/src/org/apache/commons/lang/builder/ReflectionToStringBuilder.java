// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang.builder;

import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@Deprecated(forRemoval = true)
public final class ReflectionToStringBuilder extends org.apache.commons.lang3.builder.ReflectionToStringBuilder {
  public ReflectionToStringBuilder(Object object) {
    super(object);
  }

  public ReflectionToStringBuilder(Object object, ToStringStyle style) {
    super(object, style);
  }

  public ReflectionToStringBuilder(Object object, ToStringStyle style, StringBuffer buffer) {
    super(object, style, buffer);
  }

  public <T> ReflectionToStringBuilder(T object,
                                       ToStringStyle style,
                                       StringBuffer buffer,
                                       Class<? super T> reflectUpToClass,
                                       boolean outputTransients,
                                       boolean outputStatics) {
    super(object, style, buffer, reflectUpToClass, outputTransients, outputStatics);
  }

  public <T> ReflectionToStringBuilder(T object,
                                       ToStringStyle style,
                                       StringBuffer buffer,
                                       Class<? super T> reflectUpToClass,
                                       boolean outputTransients,
                                       boolean outputStatics,
                                       boolean excludeNullValues) {
    super(object, style, buffer, reflectUpToClass, outputTransients, outputStatics, excludeNullValues);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static String toString(Object object, ToStringStyle style) {
    return org.apache.commons.lang3.builder.ReflectionToStringBuilder.toString(object, style);
  }
}
