// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang.text;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@Deprecated(forRemoval = true)
public final class StrBuilder extends org.apache.commons.lang3.text.StrBuilder {
  public StrBuilder() {
  }

  public StrBuilder(int initialCapacity) {
    super(initialCapacity);
  }

  public StrBuilder(String str) {
    super(str);
  }
}
