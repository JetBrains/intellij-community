// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang.builder;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public final class HashCodeBuilder extends org.apache.commons.lang3.builder.HashCodeBuilder {
  public HashCodeBuilder() {
  }

  public HashCodeBuilder(int initialOddNumber, int multiplierOddNumber) {
    super(initialOddNumber, multiplierOddNumber);
  }

  @Override
  public HashCodeBuilder append(boolean value) {
    super.append(value);
    return this;
  }

  @Override
  public HashCodeBuilder append(int value) {
    super.append(value);
    return this;
  }

  @Override
  public HashCodeBuilder append(Object array) {
    super.append(array);
    return this;
  }

  @Override
  public HashCodeBuilder appendSuper(final int superHashCode) {
    super.appendSuper(superHashCode);
    return this;
  }

  public static int reflectionHashCode(Object object) {
    return reflectionHashCode(17, 37, object, false);
  }
}
