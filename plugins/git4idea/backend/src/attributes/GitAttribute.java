// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.attributes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public enum GitAttribute {
  TEXT("text"),
  CRLF("crlf");

  private final @NotNull String myName;

  GitAttribute(@NotNull @NonNls String name) {
    myName = name;
  }

  public @NonNls @NotNull String getName() {
    return myName;
  }

  public static @Nullable GitAttribute forName(String attribute) {
    for (GitAttribute attr : values()) {
      if (attr.myName.equalsIgnoreCase(attribute)) {
        return attr;
      }
    }
    return null;
  }
}
