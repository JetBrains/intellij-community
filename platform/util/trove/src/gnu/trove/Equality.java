// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove;

public interface Equality<T> {
  Equality CANONICAL = new CanonicalEquality();
  Equality IDENTITY = new IdentityEquality();

  boolean equals(T o1, T o2);
}
