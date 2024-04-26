// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang.builder;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@Deprecated(forRemoval = true)
public final class EqualsBuilder extends org.apache.commons.lang3.builder.EqualsBuilder {
  @Override
  public EqualsBuilder append(Object lhs, Object rhs) {
    return (EqualsBuilder)super.append(lhs, rhs);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static boolean reflectionEquals(Object lhs, Object rhs) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(lhs, rhs);
  }

  @Override
  public EqualsBuilder append(int lhs, int rhs) {
    super.append(lhs, rhs);
    return this;
  }

  @Override
  public EqualsBuilder append(boolean lhs, boolean rhs) {
    super.append(lhs, rhs);
    return this;
  }

  @Override
  public EqualsBuilder appendSuper(boolean superEquals) {
    super.appendSuper(superEquals);
    return this;
  }
}
