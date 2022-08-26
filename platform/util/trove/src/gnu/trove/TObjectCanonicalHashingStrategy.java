// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove;

import java.util.Objects;

/**
 * @author dyoma
 */
class TObjectCanonicalHashingStrategy<T> implements TObjectHashingStrategy<T> {
  @Override
  public int computeHashCode(T value) {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public boolean equals(T value, T value1) {
    return Objects.equals(value, value1);
  }
}
