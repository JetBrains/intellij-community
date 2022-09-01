// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove;

import java.util.Objects;

public interface Equality<T> {
  Equality CANONICAL = new CanonicalEquality();
  Equality IDENTITY = new IdentityEquality();

  boolean equals(T o1, T o2);
}

final class IdentityEquality<T> implements Equality<T> {
  @Override
  public boolean equals(T o1, T o2) {
    return o1 == o2;
  }
}

final class CanonicalEquality<T> implements Equality<T> {
  @Override
  public boolean equals(T o1, T o2) {
    return Objects.equals(o1, o2);
  }
}